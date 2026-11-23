package io.koraframework.logging.symbol.processor.aop

import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSValueParameter
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import io.koraframework.aop.symbol.processor.KoraAspect
import io.koraframework.ksp.common.AnnotationUtils.findValue
import io.koraframework.ksp.common.AnnotationUtils.isAnnotationPresent
import io.koraframework.ksp.common.CommonClassNames
import io.koraframework.ksp.common.FunctionUtils.isCompletionStage
import io.koraframework.ksp.common.FunctionUtils.isFlux
import io.koraframework.ksp.common.FunctionUtils.isFuture
import io.koraframework.ksp.common.FunctionUtils.isMono
import io.koraframework.ksp.common.FunctionUtils.isSuspend
import io.koraframework.ksp.common.FunctionUtils.isVoid
import io.koraframework.ksp.common.KspCommonUtils.findRepeatableAnnotation
import io.koraframework.ksp.common.exception.ProcessingErrorException
import java.util.concurrent.CompletionStage

class MdcKoraAspect : KoraAspect {

    companion object {
        const val MDC_CONTEXT_VAL_NAME = "__mdcContext"
        val mdc = ClassName("io.koraframework.logging.common", "MDC")
        val mdcWriter = ClassName("io.koraframework.logging.common.arg", "StructuredArgumentWriter")
        val mdcAnnotation = ClassName("io.koraframework.logging.common.annotation", "Mdc")
        val mdcContainerAnnotation = mdcAnnotation.nestedClass("MdcContainer")

        // Parameter types that have a dedicated MDC.put overload and keep their JSON type
        private val NATIVE_MDC_TYPES = setOf(
            String::class.qualifiedName!!,
            Int::class.qualifiedName!!,
            Long::class.qualifiedName!!,
            Boolean::class.qualifiedName!!,
            mdcWriter.canonicalName,
        )
    }

    override fun getSupportedAnnotationTypes(): Set<String> = setOf(mdcAnnotation.canonicalName, mdcContainerAnnotation.canonicalName)

    override fun apply(
        ksFunction: KSFunctionDeclaration,
        superCall: String,
        aspectContext: KoraAspect.AspectContext
    ): KoraAspect.ApplyResult {
        val annotations = ksFunction.findRepeatableAnnotation(mdcAnnotation, mdcContainerAnnotation)
            .toList()

        val parametersWithAnnotation = ksFunction.parameters
            .filter { it.isAnnotationPresent(mdcAnnotation) }

        if (annotations.isEmpty() && parametersWithAnnotation.isEmpty()) {
            return KoraAspect.ApplyResult.MethodBody(ksFunction.superCall(superCall))
        }

        if (ksFunction.isFuture()) {
            throw ProcessingErrorException("@Mdc can't be applied for types assignable from ${CommonClassNames.future}", ksFunction)
        } else if (ksFunction.isCompletionStage()) {
            throw ProcessingErrorException("@Mdc can't be applied for types assignable from ${CompletionStage::class.java}", ksFunction)
        } else if (ksFunction.isMono() || ksFunction.isFlux()) {
            throw ProcessingErrorException("@Mdc can't be applied for types assignable from ${CommonClassNames.publisher}", ksFunction)
        }

        val currentContextBuilder = CodeBlock.builder()
        currentContextBuilder.addStatement("val %N = %T.get().values()", MDC_CONTEXT_VAL_NAME, mdc)
        val fillMdcBuilder = CodeBlock.builder()
        val previousValueVars = LinkedHashMap<String, String>()
        fillMdcByMethodAnnotations(annotations, previousValueVars, currentContextBuilder, fillMdcBuilder, !ksFunction.isSuspend())
        fillMdcByParametersAnnotations(parametersWithAnnotation, previousValueVars, currentContextBuilder, fillMdcBuilder, !ksFunction.isSuspend())
        val clearMdcBuilder = CodeBlock.builder()
        clearMdc(previousValueVars, clearMdcBuilder)

        return CodeBlock.builder()
            .add(currentContextBuilder.build())
            .add(if (ksFunction.isVoid()) "" else "return ")
            .beginControlFlow("try")
            .add(fillMdcBuilder.build())
            .addStatement("%L", ksFunction.superCall(superCall))
            .endControlFlow()
            .beginControlFlow("finally")
            .add(clearMdcBuilder.build())
            .endControlFlow()
            .build()
            .let { KoraAspect.ApplyResult.MethodBody(it) }
    }

    private fun fillMdcByMethodAnnotations(
        annotations: List<KSAnnotation>,
        previousValueVars: MutableMap<String, String>,
        currentContextBuilder: CodeBlock.Builder,
        fillMdcBuilder: CodeBlock.Builder,
        globalIsSupported: Boolean
    ) {
        for (annotation in annotations) {
            val key: String = annotation.findValue<String>("key")
                .orEmpty()
                .ifBlank { throw ProcessingErrorException("@Mdc annotation must have 'key' attribute", annotation.annotationType) }
            val value: String = annotation.findValue<String>("value")
                .orEmpty()
                .ifBlank { throw ProcessingErrorException("@Mdc annotation must have 'value' attribute", annotation.annotationType) }
            val global = annotation.findValue("global") ?: false

            if (!global) {
                savePreviousValue(key, previousValueVars, currentContextBuilder)
            } else if (!globalIsSupported) {
                throw ProcessingErrorException("@Mdc annotation with 'global' attribute is not supported for this function", annotation.annotationType)
            }
            if (value.startsWith("\${") && value.endsWith("}")) {
                fillMdcBuilder.addStatement("%T.put(%S, %L)", mdc, key, value.substring(2, value.length - 1))
            } else {
                fillMdcBuilder.addStatement("%T.put(%S, %S)", mdc, key, value)
            }
        }
    }

    private fun fillMdcByParametersAnnotations(
        parametersWithAnnotation: List<KSValueParameter>,
        previousValueVars: MutableMap<String, String>,
        currentContextBuilder: CodeBlock.Builder,
        fillMdcBuilder: CodeBlock.Builder,
        globalIsSupported: Boolean
    ) {
        for (parameter in parametersWithAnnotation) {
            val parameterName = parameter.name?.asString()
            val annotation = parameter.findRepeatableAnnotation(mdcAnnotation, mdcContainerAnnotation)
                .first()
            val key: String = annotation.findValue<String?>("key")
                ?.ifBlank { annotation.findValue("value") }
                ?.ifBlank { parameterName }
                ?: throw ProcessingErrorException("@Mdc annotation must have key or value or parameter name", parameter)

            val global = annotation.findValue("global") ?: false

            val type = parameter.type.resolve()
            when {
                isNativeMdcType(type) -> fillMdcBuilder.addStatement("%T.put(%S, %N)", mdc, key, parameterName)
                type.isMarkedNullable -> fillMdcBuilder
                    .beginControlFlow("if (%N != null)", parameterName)
                    .addStatement("%T.put(%S, %N.toString())", mdc, key, parameterName)
                    .endControlFlow()

                else -> fillMdcBuilder.addStatement("%T.put(%S, %N.toString())", mdc, key, parameterName)
            }

            if (!global) {
                savePreviousValue(key, previousValueVars, currentContextBuilder)
            } else if (!globalIsSupported) {
                throw ProcessingErrorException("@Mdc annotation with 'global' attribute is not supported for this function", annotation.annotationType)
            }
        }
    }

    private fun savePreviousValue(key: String, previousValueVars: MutableMap<String, String>, currentContextBuilder: CodeBlock.Builder) {
        if (key !in previousValueVars) {
            val varName = "__mdcPrev" + previousValueVars.size
            previousValueVars[key] = varName
            currentContextBuilder.addStatement("val %N = %N[%S]", varName, MDC_CONTEXT_VAL_NAME, key)
        }
    }

    private fun isNativeMdcType(type: KSType): Boolean =
        type.declaration.qualifiedName?.asString() in NATIVE_MDC_TYPES

    private fun clearMdc(previousValueVars: Map<String, String>, b: CodeBlock.Builder) = previousValueVars.forEach { (key, varName) ->
        b.beginControlFlow("if (%N != null)", varName)
            .addStatement("%T.put(%S, %N)", mdc, key, varName)
            .endControlFlow()
            .beginControlFlow("else")
            .addStatement("%T.remove(%S)", mdc, key)
            .endControlFlow()
    }
}
