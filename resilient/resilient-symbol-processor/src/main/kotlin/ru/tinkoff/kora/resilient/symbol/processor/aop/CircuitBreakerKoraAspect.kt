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
            throw ProcessingErrorException(
                ProcessingError(
                    "@CircuitBreaker can't be applied for types assignable from ${Future::class.java}", method, Diagnostic.Kind.NOTE
                )
            )
        }
        if (method.isVoid()) {
            throw ProcessingErrorException(
                ProcessingError(
                    "@CircuitBreaker can't be applied for types assignable from ${Void::class.java}", method, Diagnostic.Kind.NOTE
                )
            )
        }
        if (method.isMono()) {
            throw ProcessingErrorException(
                ProcessingError(
                    "@CircuitBreaker can't be applied for types assignable from ${Mono::class.java}", method, Diagnostic.Kind.NOTE
                )
            )
        }
        if (method.isFlux()) {
            throw ProcessingErrorException(
                ProcessingError(
                    "@CircuitBreaker can't be applied for types assignable from ${Flux::class.java}", method, Diagnostic.Kind.NOTE
                )
            )
        }

        val annotation = method.annotations.asSequence().filter { a -> a.annotationType.resolve().toClassName().canonicalName == ANNOTATION_TYPE }.first()
        val circuitBreakerName = annotation.arguments.asSequence().filter { arg -> arg.name!!.getShortName() == "value" }.map { arg -> arg.value.toString() }.first()
        val fallbackMethod = annotation.arguments.asSequence().filter { arg -> arg.name!!.getShortName() == "fallbackMethod" }.map { arg -> arg.value.toString() }.firstOrNull() ?: ""

        val managerType = resolver.getClassDeclarationByName("ru.tinkoff.kora.resilient.circuitbreaker.CircuitBreakerManager")!!.asType(listOf())
        val fieldManager = aspectContext.fieldFactory.constructorParam(managerType, listOf())

        val body = if (method.isFlow()) {
            buildBodyFlow(method, fallbackMethod, superCall, circuitBreakerName, fieldManager)
        } else if (method.isSuspend()) {
            buildBodySuspend(method, fallbackMethod, superCall, circuitBreakerName, fieldManager)
        } else {
            buildBodySync(method, fallbackMethod, superCall, circuitBreakerName, fieldManager)
        }

        return KoraAspect.ApplyResult.MethodBody(body)
    }

    private fun buildBodySync(
        method: KSFunctionDeclaration, fallbackCall: String, superCall: String, circuitBreakerName: String, fieldManager: String
    ): CodeBlock {
        val superMethod = buildMethodCall(method, superCall)
        val fallbackMethod = if (fallbackCall.isEmpty()) CodeBlock.of("throw e")
        else CodeBlock.of("return %L", buildMethodCall(method, fallbackCall))
        return CodeBlock.builder().add(
            """
                      val circuitBreaker = %L["%L"]

                      return try {
                          circuitBreaker.acquire()
                          val t = %L;
                          circuitBreaker.releaseOnSuccess()
                          t
                      } catch (e: ru.tinkoff.kora.resilient.circuitbreaker.CallNotPermittedException) {
                          %L
                      } catch (e: java.lang.Exception) {
                          circuitBreaker.releaseOnError(e)
                          throw e
                      }
                """.trimIndent(), fieldManager, circuitBreakerName, superMethod.toString(), fallbackMethod.toString()
        ).build()
    }

    private fun buildBodySuspend(
        method: KSFunctionDeclaration, fallbackCall: String, superCall: String, circuitBreakerName: String, fieldManager: String
    ): CodeBlock {
        val superMethod = buildMethodCall(method, superCall)
        val fallbackMethod = if (fallbackCall.isEmpty()) CodeBlock.of("throw e")
        else CodeBlock.of("return %L", buildMethodCall(method, fallbackCall))
        return CodeBlock.builder().add(
            """
                      val circuitBreaker = %L["%L"]

                      return try {
                          circuitBreaker.acquire()
                          val t = %L;
                          circuitBreaker.releaseOnSuccess()
                          t
                      } catch (e: ru.tinkoff.kora.resilient.circuitbreaker.CallNotPermittedException) {
                          %L
                      } catch (e: java.lang.Exception) {
                          circuitBreaker.releaseOnError(e)
                          throw e
                      }
                """.trimIndent(), fieldManager, circuitBreakerName, superMethod.toString(), fallbackMethod.toString()
        ).build()
    }

    private fun buildBodyFlow(
        method: KSFunctionDeclaration, fallbackCall: String, superCall: String, circuitBreakerName: String, fieldManager: String
    ): CodeBlock {
        val flowMember = MemberName("kotlinx.coroutines.flow", "flow")
        val emitMember = MemberName("kotlinx.coroutines.flow", "emitAll")
        val superMethod = buildMethodCall(method, superCall)
        val fallbackMethod = if (fallbackCall.isEmpty()) CodeBlock.of("throw e;")
        else CodeBlock.of("emitAll(%L)", buildMethodCall(method, fallbackCall))
        return CodeBlock.builder().add(
            """
                val circuitBreaker = %L["%L"]

                return %M {
                    try {
                        circuitBreaker.acquire()
                        val t = %L;
                        %M(t)
                        circuitBreaker.releaseOnSuccess()
                    } catch (e: ru.tinkoff.kora.resilient.circuitbreaker.CallNotPermittedException) {
                        %L
                    } catch (e: java.lang.Exception) {
                        circuitBreaker.releaseOnError(e)
                        throw e
                    }
                }
                    """.trimIndent(), fieldManager, circuitBreakerName, flowMember, superMethod.toString(), emitMember, fallbackMethod.toString()
        ).build()
    }

    private fun buildMethodCall(method: KSFunctionDeclaration, call: String): CodeBlock {
        return CodeBlock.of(method.parameters.asSequence().map { p -> CodeBlock.of("%L", p) }.joinToString(", ", "$call(", ")"))
    }
}
