package ru.tinkoff.kora.logging.symbol.processor.aop.mdc

import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSValueParameter
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import ru.tinkoff.kora.aop.symbol.processor.KoraAspect
import ru.tinkoff.kora.ksp.common.AnnotationUtils.findValue
import ru.tinkoff.kora.ksp.common.AnnotationUtils.isAnnotationPresent
import ru.tinkoff.kora.ksp.common.CommonClassNames
import ru.tinkoff.kora.ksp.common.FunctionUtils.isCompletionStage
import ru.tinkoff.kora.ksp.common.FunctionUtils.isFlux
import ru.tinkoff.kora.ksp.common.FunctionUtils.isFuture
import ru.tinkoff.kora.ksp.common.FunctionUtils.isMono
import ru.tinkoff.kora.ksp.common.FunctionUtils.isSuspend
import ru.tinkoff.kora.ksp.common.FunctionUtils.isVoid
import ru.tinkoff.kora.ksp.common.KspCommonUtils.findRepeatableAnnotation
import ru.tinkoff.kora.ksp.common.exception.ProcessingErrorException
import java.util.concurrent.CompletionStage

class MdcKoraAspect : KoraAspect {

    companion object {
        const val MDC_CONTEXT_VAL_NAME = "__mdcContext"
        val mdc = ClassName("ru.tinkoff.kora.logging.common", "MDC")
        val mdcAnnotation = ClassName("ru.tinkoff.kora.logging.common.annotation", "Mdc")
        val mdcContainerAnnotation = mdcAnnotation.nestedClass("MdcContainer")
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
        val methodKeys = fillMdcByMethodAnnotations(annotations, currentContextBuilder, fillMdcBuilder, !ksFunction.isSuspend())
        val parameterKeys = fillMdcByParametersAnnotations(parametersWithAnnotation, currentContextBuilder, fillMdcBuilder, !ksFunction.isSuspend())
        val clearMdcBuilder = CodeBlock.builder()
        clearMdc(methodKeys, clearMdcBuilder)
        clearMdc(parameterKeys, clearMdcBuilder)

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
        currentContextBuilder: CodeBlock.Builder,
        fillMdcBuilder: CodeBlock.Builder,
        globalIsSupported: Boolean
    ): Set<String> {
        val keys: MutableSet<String> = HashSet()
        for (annotation in annotations) {
            val key: String = annotation.findValue("key")
                ?: throw ProcessingErrorException("@Mdc annotation must have 'key' attribute", annotation.annotationType)
            val value: String = annotation.findValue("value")
                ?: throw ProcessingErrorException("@Mdc annotation must have 'value' attribute", annotation.annotationType)
            val global = annotation.findValue("global") ?: false

            if (!global) {
                keys.add(key)
                currentContextBuilder.addStatement("val __%L = %N[%S]", key, MDC_CONTEXT_VAL_NAME, key)
            } else if (!globalIsSupported) {
                throw ProcessingErrorException("@Mdc annotation with 'global' attribute is not supported for this function", annotation.annotationType)
            }
            fillMdcBuilder.addStatement(
                "%T.put(%S, %S)",
                mdc,
                key,
                value
            )
        }
        return keys
    }

    private fun fillMdcByParametersAnnotations(
        parametersWithAnnotation: List<KSValueParameter>,
        currentContextBuilder: CodeBlock.Builder,
        fillMdcBuilder: CodeBlock.Builder,
        globalIsSupported: Boolean
    ): Set<String> {
        val keys: MutableSet<String> = HashSet()
        for (parameter in parametersWithAnnotation) {
            val parameterName = parameter.name?.asString()
            val annotation = parameter.findRepeatableAnnotation(mdcAnnotation, mdcContainerAnnotation)
                .first()
            val key: String = annotation.findValue<String?>("key")
                ?.ifBlank { annotation.findValue("value") }
                ?.ifBlank { parameterName }
                ?: throw ProcessingErrorException("@Mdc annotation must have key or value or parameter name", parameter)

            val global = annotation.findValue("global") ?: false

            fillMdcBuilder.addStatement(
                "%T.put(%S, %N)",
                mdc,
                key,
                parameterName
            )

            if (!global) {
                keys.add(key)
                currentContextBuilder.addStatement("val __%L = %N[%S]", key, MDC_CONTEXT_VAL_NAME, key)
            } else if (!globalIsSupported) {
                throw ProcessingErrorException("@Mdc annotation with 'global' attribute is not supported for this function", annotation.annotationType)
            }
        }

        return keys
    }

    private fun clearMdc(keys: Set<String>, b: CodeBlock.Builder) = keys.forEach {
        b.beginControlFlow("if (__%L != null)", it)
            .addStatement("%T.put(%S, __%L)", mdc, it, it)
            .endControlFlow()
            .beginControlFlow("else")
            .addStatement("%T.remove(%S)", mdc, it)
            .endControlFlow()
    }
}
