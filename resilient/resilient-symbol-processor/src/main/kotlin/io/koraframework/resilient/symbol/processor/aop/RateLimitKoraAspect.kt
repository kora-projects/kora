package io.koraframework.resilient.symbol.processor.aop

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
import io.koraframework.ksp.common.FunctionUtils.isCompletionStage
import io.koraframework.ksp.common.FunctionUtils.isFlow
import io.koraframework.ksp.common.FunctionUtils.isFlux
import io.koraframework.ksp.common.FunctionUtils.isFuture
import io.koraframework.ksp.common.FunctionUtils.isMono
import io.koraframework.ksp.common.FunctionUtils.isVoid
import io.koraframework.ksp.common.exception.ProcessingErrorException
import java.util.concurrent.CompletionStage
import java.util.concurrent.Future

class RateLimitKoraAspect(val resolver: Resolver) : KoraAspect {

    companion object {
        private val ANNOTATION_TYPE = ClassName("io.koraframework.resilient.ratelimiter.annotation", "RateLimited")
        private val RATE_LIMITER = ClassName("io.koraframework.resilient.ratelimiter", "RateLimiter")
        private val EXCEEDED_EXCEPTION = ClassName("io.koraframework.resilient.ratelimiter.exception", "RateLimitExceededException")
    }

    override fun getSupportedAnnotationTypes(): Set<String> {
        return setOf(ANNOTATION_TYPE.canonicalName)
    }

    override fun apply(ksFunction: KSFunctionDeclaration, superCall: String, aspectContext: KoraAspect.AspectContext): KoraAspect.ApplyResult {
        if (ksFunction.isFuture()) {
            throw ProcessingErrorException("@RateLimited can't be applied for types assignable from ${Future::class.java}", ksFunction)
        } else if (ksFunction.isCompletionStage()) {
            throw ProcessingErrorException("@RateLimited can't be applied for types assignable from ${CompletionStage::class.java}", ksFunction)
        } else if (ksFunction.isMono()) {
            throw ProcessingErrorException("@RateLimited can't be applied for types assignable from ${CommonClassNames.mono}", ksFunction)
        } else if (ksFunction.isFlux()) {
            throw ProcessingErrorException("@RateLimited can't be applied for types assignable from ${CommonClassNames.flux}", ksFunction)
        }

        val rateLimiterType = ksFunction.findAnnotation(ANNOTATION_TYPE)!!
            .findValue<KSType>("value")!!
        val baseRateLimiter = resolver.getClassDeclarationByName(RATE_LIMITER.canonicalName)!!.asStarProjectedType()
        if (!baseRateLimiter.isAssignableFrom(rateLimiterType)) {
            throw ProcessingErrorException("@RateLimited value must extend ${RATE_LIMITER.canonicalName}", ksFunction)
        }
        val fieldRateLimiter = aspectContext.fieldFactory.constructorParam(
            rateLimiterType.toTypeName(),
            listOf()
        )

        val body = if (ksFunction.isFlow()) {
            buildBodyFlow(ksFunction, superCall, fieldRateLimiter)
        } else {
            buildBodySync(ksFunction, superCall, fieldRateLimiter)
        }
        return KoraAspect.ApplyResult.MethodBody(body)
    }

    private fun buildBodySync(
        method: KSFunctionDeclaration, superCall: String, fieldRateLimiter: String
    ): CodeBlock {
        val superMethod = buildMethodCall(method, superCall)
        val methodCall = if (method.isVoid()) superMethod else CodeBlock.of("val t = %L", superMethod)
        val returnCall = if (method.isVoid()) CodeBlock.of("") else CodeBlock.of("t")

        return CodeBlock.builder().add(
            """
            return try {
                %L.acquire()
                %L
                %L
            } catch (e: %T) {
                throw e
            } catch (e: Throwable) {
                throw e
            }
            """.trimIndent(),
            fieldRateLimiter, methodCall, returnCall, EXCEEDED_EXCEPTION
        ).build()
    }

    private fun buildBodyFlow(
        method: KSFunctionDeclaration, superCall: String, fieldRateLimiter: String
    ): CodeBlock {
        val flowMember = MemberName("kotlinx.coroutines.flow", "flow")
        val emitMember = MemberName("kotlinx.coroutines.flow", "emitAll")
        val superMethod = buildMethodCall(method, superCall)
        return CodeBlock.builder().add(
            """
            return %M {
                try {
                    %L.acquire()
                    %M(%L)
                } catch (e: %T) {
                    throw e
                } catch (e: Throwable) {
                    throw e
                }
            }
            """.trimIndent(),
            flowMember, fieldRateLimiter, emitMember, superMethod.toString(), EXCEEDED_EXCEPTION
        ).build()
    }

    private fun buildMethodCall(method: KSFunctionDeclaration, call: String): CodeBlock {
        return CodeBlock.of(method.parameters.asSequence().map { p -> CodeBlock.of("%L", p) }.joinToString(", ", "$call(", ")"))
    }
}
