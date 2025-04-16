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
import ru.tinkoff.kora.ksp.common.FunctionUtils.isFlow
import ru.tinkoff.kora.ksp.common.FunctionUtils.isFlux
import ru.tinkoff.kora.ksp.common.FunctionUtils.isCompletionStage
import ru.tinkoff.kora.ksp.common.FunctionUtils.isFuture
import ru.tinkoff.kora.ksp.common.FunctionUtils.isMono
import ru.tinkoff.kora.ksp.common.FunctionUtils.isSuspend
import ru.tinkoff.kora.ksp.common.FunctionUtils.isVoid
import ru.tinkoff.kora.ksp.common.exception.ProcessingErrorException
import java.util.concurrent.CompletionStage
import java.util.concurrent.Future

class CircuitBreakerKoraAspect(val resolver: Resolver) : KoraAspect {

    companion object {
        private val ANNOTATION_TYPE = ClassName("ru.tinkoff.kora.resilient.circuitbreaker.annotation", "CircuitBreaker")
        private val PERMITTED_EXCEPTION = ClassName("ru.tinkoff.kora.resilient.circuitbreaker", "CallNotPermittedException")
    }

    override fun getSupportedAnnotationTypes(): Set<String> {
        return setOf(ANNOTATION_TYPE.canonicalName)
    }

    override fun apply(ksFunction: KSFunctionDeclaration, superCall: String, aspectContext: KoraAspect.AspectContext): KoraAspect.ApplyResult {
        if (ksFunction.isFuture()) {
            throw ProcessingErrorException("@CircuitBreaker can't be applied for types assignable from ${Future::class.java}", ksFunction)
        } else if (ksFunction.isCompletionStage()) {
            throw ProcessingErrorException("@CircuitBreaker can't be applied for types assignable from ${CompletionStage::class.java}", ksFunction)
        } else if (ksFunction.isMono()) {
            throw ProcessingErrorException("@CircuitBreaker can't be applied for types assignable from ${CommonClassNames.mono}", ksFunction)
        } else if (ksFunction.isFlux()) {
            throw ProcessingErrorException("@CircuitBreaker can't be applied for types assignable from ${CommonClassNames.flux}", ksFunction)
        }

        val circuitBreakerName = ksFunction.findAnnotation(ANNOTATION_TYPE)!!
            .findValue<String>("value")!!

        val managerType = resolver.getClassDeclarationByName("ru.tinkoff.kora.resilient.circuitbreaker.CircuitBreakerManager")!!.asType(listOf())
        val fieldManager = aspectContext.fieldFactory.constructorParam(managerType, listOf())
        val circuitType = resolver.getClassDeclarationByName("ru.tinkoff.kora.resilient.circuitbreaker.CircuitBreaker")!!.asType(listOf())
        val fieldCircuit = aspectContext.fieldFactory.constructorInitialized(
            circuitType,
            CodeBlock.of("%L[%S]", fieldManager, circuitBreakerName)
        )

        val body = if (ksFunction.isFlow()) {
            buildBodyFlow(ksFunction, superCall, fieldCircuit)
        } else if (ksFunction.isSuspend()) {
            buildBodySync(ksFunction, superCall, fieldCircuit)
        } else {
            buildBodySync(ksFunction, superCall, fieldCircuit)
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
            } catch (e: %T) {
                throw e
            } catch (e: Throwable) {
                %L.releaseOnError(e)
                throw e
            }
            """.trimIndent(), fieldCircuitBreaker, methodCall, fieldCircuitBreaker,
            returnCall, PERMITTED_EXCEPTION, fieldCircuitBreaker
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
                } catch (e: %T) {
                    throw e
                } catch (e: Throwable) {
                    %L.releaseOnError(e)
                    throw e
                }
            }
            """.trimIndent(), flowMember, fieldCircuitBreaker, emitMember, superMethod.toString(),
            fieldCircuitBreaker, PERMITTED_EXCEPTION, fieldCircuitBreaker
        ).build()
    }

    private fun buildMethodCall(method: KSFunctionDeclaration, call: String): CodeBlock {
        return CodeBlock.of(method.parameters.asSequence().map { p -> CodeBlock.of("%L", p) }.joinToString(", ", "$call(", ")"))
    }
}
