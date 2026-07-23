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
import io.koraframework.ksp.common.FunctionUtils.isSuspend
import io.koraframework.ksp.common.FunctionUtils.isVoid
import io.koraframework.ksp.common.exception.ProcessingErrorException
import java.util.concurrent.CompletionStage
import java.util.concurrent.Future

class CircuitBreakerKoraAspect(val resolver: Resolver) : KoraAspect {

    companion object {
        private val ANNOTATION_TYPE = ClassName("io.koraframework.resilient.circuitbreaker.annotation", "CircuitBreakable")
        private val CIRCUIT_BREAKER = ClassName("io.koraframework.resilient.circuitbreaker", "CircuitBreaker")
        private val PERMITTED_EXCEPTION = ClassName("io.koraframework.resilient.circuitbreaker.exception", "CallNotPermittedException")
    }

    override fun getSupportedAnnotationTypes(): Set<String> {
        return setOf(ANNOTATION_TYPE.canonicalName)
    }

    override fun apply(ksFunction: KSFunctionDeclaration, superCall: String, aspectContext: KoraAspect.AspectContext): KoraAspect.ApplyResult {
        if (ksFunction.isFuture()) {
            throw ProcessingErrorException("@CircuitBreakable can't be applied for types assignable from ${Future::class.java}", ksFunction)
        } else if (ksFunction.isCompletionStage()) {
            throw ProcessingErrorException("@CircuitBreakable can't be applied for types assignable from ${CompletionStage::class.java}", ksFunction)
        } else if (ksFunction.isMono()) {
            throw ProcessingErrorException("@CircuitBreakable can't be applied for types assignable from ${CommonClassNames.mono}", ksFunction)
        } else if (ksFunction.isFlux()) {
            throw ProcessingErrorException("@CircuitBreakable can't be applied for types assignable from ${CommonClassNames.flux}", ksFunction)
        }

        val circuitBreakerType = ksFunction.findAnnotation(ANNOTATION_TYPE)!!
            .findValue<KSType>("value")!!
        val baseCircuitBreaker = resolver.getClassDeclarationByName(CIRCUIT_BREAKER.canonicalName)!!.asStarProjectedType()
        if (!baseCircuitBreaker.isAssignableFrom(circuitBreakerType)) {
            throw ProcessingErrorException("@CircuitBreakable value must extend ${CIRCUIT_BREAKER.canonicalName}", ksFunction)
        }

        val fieldCircuit = aspectContext.fieldFactory.constructorParam(
            circuitBreakerType.toTypeName(),
            listOf()
        )

        val body = if (ksFunction.isFlow()) {
            buildBodyFlow(ksFunction, superCall, fieldCircuit)
        } else if (ksFunction.isSuspend()) {
            buildBodySuspend(ksFunction, superCall, fieldCircuit)
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

    private fun buildBodySuspend(
        method: KSFunctionDeclaration, superCall: String, fieldCircuitBreaker: String
    ): CodeBlock {
        val superMethod = buildMethodCall(method, superCall)
        val methodCall = if (method.isVoid()) superMethod else CodeBlock.of("val t = %L", superMethod)
        val returnCall = if (method.isVoid()) CodeBlock.of("") else CodeBlock.of("t")

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

    private fun buildMethodCall(method: KSFunctionDeclaration, call: String): CodeBlock {
        return CodeBlock.of(method.parameters.asSequence().map { p -> CodeBlock.of("%L", p) }.joinToString(", ", "$call(", ")"))
    }
}
