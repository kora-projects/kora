package io.koraframework.resilient.symbol.processor.aop

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ksp.toTypeName
import io.koraframework.aop.symbol.processor.KoraAspect
import io.koraframework.ksp.common.AnnotationUtils.findAnnotation
import io.koraframework.ksp.common.AnnotationUtils.findValue
import io.koraframework.ksp.common.CommonClassNames
import io.koraframework.ksp.common.FunctionUtils.isFlux
import io.koraframework.ksp.common.FunctionUtils.isCompletionStage
import io.koraframework.ksp.common.FunctionUtils.isFuture
import io.koraframework.ksp.common.FunctionUtils.isMono
import io.koraframework.ksp.common.FunctionUtils.isVoid
import io.koraframework.ksp.common.exception.ProcessingErrorException
import java.util.concurrent.CompletionStage
import java.util.concurrent.Future

@KspExperimental
class TimeoutKoraAspect(val resolver: Resolver) : KoraAspect {

    companion object {
        private val ANNOTATION_TYPE = ClassName("io.koraframework.resilient.timeout.annotation", "Timeout")
        private val TIMEOUT = ClassName("io.koraframework.resilient.timeout", "Timeouter")
        val MEMBER_CALLABLE = MemberName("java.util.concurrent", "Callable")
    }

    override fun getSupportedAnnotationTypes(): Set<String> {
        return setOf(ANNOTATION_TYPE.canonicalName)
    }

    override fun apply(ksFunction: KSFunctionDeclaration, superCall: String, aspectContext: KoraAspect.AspectContext): KoraAspect.ApplyResult {
        if (ksFunction.isFuture()) {
            throw ProcessingErrorException(unsupportedReturnTypeError("@Timeout", ksFunction, Future::class.java), ksFunction)
        } else if (ksFunction.isCompletionStage()) {
            throw ProcessingErrorException(unsupportedReturnTypeError("@Timeout", ksFunction, CompletionStage::class.java), ksFunction)
        } else if (ksFunction.isMono()) {
            throw ProcessingErrorException(unsupportedReturnTypeError("@Timeout", ksFunction, CommonClassNames.mono), ksFunction)
        } else if (ksFunction.isFlux()) {
            throw ProcessingErrorException(unsupportedReturnTypeError("@Timeout", ksFunction, CommonClassNames.flux), ksFunction)
        }

        val timeoutType = ksFunction.findAnnotation(ANNOTATION_TYPE)!!
            .findValue<KSType>("value")!!
        val baseTimeout = resolver.getClassDeclarationByName(TIMEOUT.canonicalName)!!.asStarProjectedType()
        if (!baseTimeout.isAssignableFrom(timeoutType)) {
            throw ProcessingErrorException(invalidResilientContractError("@Timeout", ksFunction, TIMEOUT.canonicalName), ksFunction)
        }
        val fieldTimeout = aspectContext.fieldFactory.constructorParam(
            timeoutType.toTypeName(),
            listOf()
        )

        val body = buildBodySync(ksFunction, superCall, fieldTimeout)

        return KoraAspect.ApplyResult.MethodBody(body)
    }

    private fun buildBodySync(
        method: KSFunctionDeclaration, superCall: String, timeoutName: String
    ): CodeBlock {
        val superMethod = buildMethodCall(method, superCall)
        return if (method.isVoid()) {
            CodeBlock.builder().add(
                """
                    %L.execute(io.koraframework.resilient.common.ThrowableRunnable { %L })
                    """.trimIndent(), timeoutName, superMethod.toString()
            ).build()
        } else {
            CodeBlock.builder().add(
                """
                    return %L.execute(io.koraframework.resilient.common.ThrowableCallable { %L })
                    """.trimIndent(), timeoutName, superMethod.toString()
            ).build()
        }
    }

    private fun buildMethodCall(method: KSFunctionDeclaration, call: String): CodeBlock {
        return CodeBlock.of(method.parameters.asSequence().map { p -> CodeBlock.of("%L", p) }.joinToString(", ", "$call(", ")"))
    }
}
