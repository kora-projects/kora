package ru.tinkoff.kora.resilient.symbol.processor.aop

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.MemberName
import ru.tinkoff.kora.aop.symbol.processor.KoraAspect
import ru.tinkoff.kora.ksp.common.AnnotationUtils.findAnnotation
import ru.tinkoff.kora.ksp.common.AnnotationUtils.findValue
import ru.tinkoff.kora.ksp.common.CommonClassNames
import ru.tinkoff.kora.ksp.common.FunctionUtils.isCompletionStage
import ru.tinkoff.kora.ksp.common.FunctionUtils.isFlow
import ru.tinkoff.kora.ksp.common.FunctionUtils.isFlux
import ru.tinkoff.kora.ksp.common.FunctionUtils.isFuture
import ru.tinkoff.kora.ksp.common.FunctionUtils.isMono
import ru.tinkoff.kora.ksp.common.FunctionUtils.isVoid
import ru.tinkoff.kora.ksp.common.exception.ProcessingErrorException
import java.util.concurrent.CompletionStage
import java.util.concurrent.Future

class RateLimitKoraAspect(val resolver: Resolver) : KoraAspect {

    companion object {
        private val ANNOTATION_TYPE = ClassName("ru.tinkoff.kora.resilient.ratelimiter.annotation", "RateLimit")
        private val EXCEEDED_EXCEPTION = ClassName("ru.tinkoff.kora.resilient.ratelimiter", "RateLimitExceededException")
    }

    override fun getSupportedAnnotationTypes(): Set<String> {
        return setOf(ANNOTATION_TYPE.canonicalName)
    }

    override fun apply(ksFunction: KSFunctionDeclaration, superCall: String, aspectContext: KoraAspect.AspectContext): KoraAspect.ApplyResult {
        if (ksFunction.isFuture()) {
            throw ProcessingErrorException("@RateLimit can't be applied for types assignable from ${Future::class.java}", ksFunction)
        } else if (ksFunction.isCompletionStage()) {
            throw ProcessingErrorException("@RateLimit can't be applied for types assignable from ${CompletionStage::class.java}", ksFunction)
        } else if (ksFunction.isMono()) {
            throw ProcessingErrorException("@RateLimit can't be applied for types assignable from ${CommonClassNames.mono}", ksFunction)
        } else if (ksFunction.isFlux()) {
            throw ProcessingErrorException("@RateLimit can't be applied for types assignable from ${CommonClassNames.flux}", ksFunction)
        }

        val rateLimiterName = ksFunction.findAnnotation(ANNOTATION_TYPE)!!
            .findValue<String>("value")!!

        val managerType = resolver.getClassDeclarationByName("ru.tinkoff.kora.resilient.ratelimiter.RateLimiterManager")!!.asType(listOf())
        val fieldManager = aspectContext.fieldFactory.constructorParam(managerType, listOf())
        val rateLimiterType = resolver.getClassDeclarationByName("ru.tinkoff.kora.resilient.ratelimiter.RateLimiter")!!.asType(listOf())
        val fieldRateLimiter = aspectContext.fieldFactory.constructorInitialized(
            rateLimiterType,
            CodeBlock.of("%L[%S]", fieldManager, rateLimiterName)
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
