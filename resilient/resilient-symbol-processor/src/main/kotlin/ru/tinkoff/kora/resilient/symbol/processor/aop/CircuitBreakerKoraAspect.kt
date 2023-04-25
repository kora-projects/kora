package ru.tinkoff.kora.resilient.symbol.processor.aop

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ksp.toClassName
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import ru.tinkoff.kora.aop.symbol.processor.KoraAspect
import ru.tinkoff.kora.ksp.common.FunctionUtils.isFlow
import ru.tinkoff.kora.ksp.common.FunctionUtils.isFlux
import ru.tinkoff.kora.ksp.common.FunctionUtils.isFuture
import ru.tinkoff.kora.ksp.common.FunctionUtils.isMono
import ru.tinkoff.kora.ksp.common.FunctionUtils.isSuspend
import ru.tinkoff.kora.ksp.common.FunctionUtils.isVoid
import ru.tinkoff.kora.ksp.common.exception.ProcessingError
import ru.tinkoff.kora.ksp.common.exception.ProcessingErrorException
import java.util.concurrent.Future
import javax.tools.Diagnostic

@KspExperimental
class CircuitBreakerKoraAspect(val resolver: Resolver) : KoraAspect {

    companion object {
        const val ANNOTATION_TYPE: String = "ru.tinkoff.kora.resilient.circuitbreaker.annotation.CircuitBreaker"
    }

    override fun getSupportedAnnotationTypes(): Set<String> {
        return setOf(ANNOTATION_TYPE)
    }

    override fun apply(method: KSFunctionDeclaration, superCall: String, aspectContext: KoraAspect.AspectContext): KoraAspect.ApplyResult {
        if (method.isFuture()) {
            throw ProcessingErrorException("@CircuitBreaker can't be applied for types assignable from ${Future::class.java}", method)
        } else if (method.isMono()) {
            throw ProcessingErrorException("@CircuitBreaker can't be applied for types assignable from ${Mono::class.java}", method)
        } else if (method.isFlux()) {
            throw ProcessingErrorException("@CircuitBreaker can't be applied for types assignable from ${Flux::class.java}", method)
        }

        val annotation = method.annotations.filter { a -> a.annotationType.resolve().toClassName().canonicalName == ANNOTATION_TYPE }.first()
        val circuitBreakerName = annotation.arguments.asSequence().filter { arg -> arg.name!!.getShortName() == "value" }.map { arg -> arg.value.toString() }.first()

        val managerType = resolver.getClassDeclarationByName("ru.tinkoff.kora.resilient.circuitbreaker.CircuitBreakerManager")!!.asType(listOf())
        val fieldManager = aspectContext.fieldFactory.constructorParam(managerType, listOf())
        val circuitType = resolver.getClassDeclarationByName("ru.tinkoff.kora.resilient.circuitbreaker.CircuitBreaker")!!.asType(listOf())
        val fieldCircuit = aspectContext.fieldFactory.constructorInitialized(
            circuitType,
            CodeBlock.of("%L[%S]", fieldManager, circuitBreakerName)
        )

        val body = if (method.isFlow()) {
            buildBodyFlow(method, superCall, fieldCircuit)
        } else if (method.isSuspend()) {
            buildBodySync(method, superCall, fieldCircuit)
        } else {
            buildBodySync(method, superCall, fieldCircuit)
        }

        return KoraAspect.ApplyResult.MethodBody(body)
    }

    private fun buildBodySync(
        method: KSFunctionDeclaration, superCall: String, fieldCircuitBreaker: String
    ): CodeBlock {
        val superMethod = buildMethodCall(method, superCall)
        val methodCall = if(method.isVoid()) superMethod else CodeBlock.of("val t = %L", superMethod)
        val returnCall = if(method.isVoid()) CodeBlock.of("") else CodeBlock.of("t")

        return CodeBlock.builder().add(
            """
            return try {
                %L.acquire()
                %L
                %L.releaseOnSuccess()
                %L
            } catch (e: ru.tinkoff.kora.resilient.circuitbreaker.CallNotPermittedException) {
                throw e
            } catch (e: java.lang.Exception) {
                %L.releaseOnError(e)
                throw e
            }
            """.trimIndent(), fieldCircuitBreaker, methodCall, fieldCircuitBreaker, returnCall, fieldCircuitBreaker
        ).build()
    }

    private fun buildBodyFlow(
        method: KSFunctionDeclaration, superCall: String, fieldCircuitBreaker: String
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
                    %L.releaseOnSuccess()
                } catch (e: ru.tinkoff.kora.resilient.circuitbreaker.CallNotPermittedException) {
                    throw e
                } catch (e: java.lang.Exception) {
                    %L.releaseOnError(e)
                    throw e
                }
            }
            """.trimIndent(), flowMember, fieldCircuitBreaker, emitMember, superMethod.toString(), fieldCircuitBreaker, fieldCircuitBreaker
        ).build()
    }

    private fun buildMethodCall(method: KSFunctionDeclaration, call: String): CodeBlock {
        return CodeBlock.of(method.parameters.asSequence().map { p -> CodeBlock.of("%L", p) }.joinToString(", ", "$call(", ")"))
    }
}
