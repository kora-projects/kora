package io.koraframework.resilient.symbol.processor.aop

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.ksp.toTypeName
import io.koraframework.aop.symbol.processor.KoraAspect
import io.koraframework.ksp.common.AnnotationUtils.findAnnotation
import io.koraframework.ksp.common.AnnotationUtils.findValue
import io.koraframework.ksp.common.CommonClassNames
import io.koraframework.ksp.common.FunctionUtils.isCompletionStage
import io.koraframework.ksp.common.FunctionUtils.isFlux
import io.koraframework.ksp.common.FunctionUtils.isFuture
import io.koraframework.ksp.common.FunctionUtils.isMono
import io.koraframework.ksp.common.FunctionUtils.isVoid
import io.koraframework.ksp.common.exception.ProcessingErrorException
import java.util.concurrent.CompletionStage
import java.util.concurrent.Future

@KspExperimental
class RetryKoraAspect(val resolver: Resolver) : KoraAspect {

    companion object {
        private val ANNOTATION_TYPE = ClassName("io.koraframework.resilient.retry.annotation", "Retryable")
        private val RETRY = ClassName("io.koraframework.resilient.retry", "Retry")

        private val RETRY_RUNNER = ClassName("io.koraframework.resilient.common", "ThrowableRunnable")
        private val RETRY_CALLABLE = ClassName("io.koraframework.resilient.common", "ThrowableCallable")
    }

    override fun getSupportedAnnotationTypes(): Set<String> {
        return setOf(ANNOTATION_TYPE.canonicalName)
    }

    override fun apply(ksFunction: KSFunctionDeclaration, superCall: String, aspectContext: KoraAspect.AspectContext): KoraAspect.ApplyResult {
        if (ksFunction.isFuture()) {
            throw ProcessingErrorException(unsupportedReturnTypeError("@Retryable", ksFunction, Future::class.java), ksFunction)
        } else if (ksFunction.isCompletionStage()) {
            throw ProcessingErrorException(unsupportedReturnTypeError("@Retryable", ksFunction, CompletionStage::class.java), ksFunction)
        } else if (ksFunction.isMono()) {
            throw ProcessingErrorException(unsupportedReturnTypeError("@Retryable", ksFunction, CommonClassNames.mono), ksFunction)
        } else if (ksFunction.isFlux()) {
            throw ProcessingErrorException(unsupportedReturnTypeError("@Retryable", ksFunction, CommonClassNames.flux), ksFunction)
        }

        val retryType = ksFunction.findAnnotation(ANNOTATION_TYPE)!!
            .findValue<KSType>("value")!!
        val baseRetry = resolver.getClassDeclarationByName(RETRY.canonicalName)!!.asStarProjectedType()
        if (!baseRetry.isAssignableFrom(retryType)) {
            throw ProcessingErrorException(invalidResilientContractError("@Retryable", ksFunction, RETRY.canonicalName), ksFunction)
        }
        val fieldRetrier = aspectContext.fieldFactory.constructorParam(
            retryType.toTypeName(),
            listOf()
        )

        val body = buildBodySync(ksFunction, superCall, fieldRetrier)

        return KoraAspect.ApplyResult.MethodBody(body)
    }

    private fun buildBodySync(method: KSFunctionDeclaration, superCall: String, fieldRetrier: String): CodeBlock {
        val builder = CodeBlock.builder()

        if (method.isVoid()) {
            builder.addStatement("%L.retry(%T { %L })", fieldRetrier, RETRY_RUNNER, buildMethodCall(method, superCall))
        } else {
            builder.addStatement("return %L.retry(%T { %L })", fieldRetrier, RETRY_CALLABLE, buildMethodCall(method, superCall))
        }

        return builder.build()
    }

    private fun buildMethodCall(method: KSFunctionDeclaration, call: String): CodeBlock {
        return CodeBlock.of(method.parameters.asSequence().map { p -> CodeBlock.of("%L", p) }.joinToString(", ", "$call(", ")"))
    }
}
