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
import ru.tinkoff.kora.ksp.common.exception.ProcessingErrorException
import java.util.concurrent.Future

@KspExperimental
class TimeoutKoraAspect(val resolver: Resolver) : KoraAspect {

    companion object {
        const val ANNOTATION_TYPE: String = "ru.tinkoff.kora.resilient.timeout.annotation.Timeout"
        val MEMBER_CALLABLE = MemberName("java.util.concurrent", "Callable")
        val timeoutMember = MemberName("kotlinx.coroutines", "withTimeout")
        val timeoutCancelMember = MemberName("kotlinx.coroutines", "TimeoutCancellationException")
        val flowMember = MemberName("kotlinx.coroutines.flow", "flow")
        val emitMember = MemberName("kotlinx.coroutines.flow", "emitAll")
        val startMember = MemberName("kotlinx.coroutines.flow", "onStart")
        val whileMember = MemberName("kotlinx.coroutines.flow", "takeWhile")
        val systemMember = MemberName("java.lang", "System")
        val atomicMember = MemberName("java.util.concurrent.atomic", "AtomicLong")
        val timeoutKoraMember = MemberName("ru.tinkoff.kora.resilient.timeout", "TimeoutExhaustedException")
    }

    override fun getSupportedAnnotationTypes(): Set<String> {
        return setOf(ANNOTATION_TYPE)
    }

    override fun apply(method: KSFunctionDeclaration, superCall: String, aspectContext: KoraAspect.AspectContext): KoraAspect.ApplyResult {
        if (method.isFuture()) {
            throw ProcessingErrorException("@Timeout can't be applied for types assignable from ${Future::class.java}", method)
        } else if (method.isMono()) {
            throw ProcessingErrorException("@Timeout can't be applied for types assignable from ${Mono::class.java}", method)
        } else if (method.isFlux()) {
            throw ProcessingErrorException("@Timeout can't be applied for types assignable from ${Flux::class.java}", method)
        }

        val annotation = method.annotations.filter { a -> a.annotationType.resolve().toClassName().canonicalName == ANNOTATION_TYPE }.first()
        val timeoutName = annotation.arguments.asSequence().filter { arg -> arg.name!!.getShortName() == "value" }.map { arg -> arg.value.toString() }.first()

        val metricType = resolver.getClassDeclarationByName("ru.tinkoff.kora.resilient.timeout.TimeoutMetrics")!!.asType(listOf()).makeNullable()
        val fieldMetric = aspectContext.fieldFactory.constructorParam(metricType, listOf())
        val managerType = resolver.getClassDeclarationByName("ru.tinkoff.kora.resilient.timeout.TimeoutManager")!!.asType(listOf())
        val fieldManager = aspectContext.fieldFactory.constructorParam(managerType, listOf())
        val fieldTimeout = aspectContext.fieldFactory.constructorInitialized(
            resolver.getClassDeclarationByName("ru.tinkoff.kora.resilient.timeout.Timeout")!!.asType(listOf()),
            CodeBlock.of("%L[%S]", fieldManager, timeoutName)
        )

        val body = if (method.isFlow()) {
            buildBodyFlow(method, superCall, timeoutName, fieldTimeout, fieldMetric)
        } else if (method.isSuspend()) {
            buildBodySuspend(method, superCall, timeoutName, fieldTimeout, fieldMetric)
        } else {
            buildBodySync(method, superCall, fieldTimeout)
        }

        return KoraAspect.ApplyResult.MethodBody(body)
    }

    private fun buildBodySync(
        method: KSFunctionDeclaration, superCall: String, timeoutName: String
    ): CodeBlock {
        val superMethod = buildMethodCall(method, superCall)
        return if (method.isVoid()) {
            CodeBlock.builder().add(
                """
                    %L.execute( %M { %L })
                    """.trimIndent(), timeoutName, MEMBER_CALLABLE, superMethod.toString()
            ).build()
        } else {
            CodeBlock.builder().add(
                """
                    return %L.execute( %M { %L })
                    """.trimIndent(), timeoutName, MEMBER_CALLABLE, superMethod.toString()
            ).build()
        }
    }

    private fun buildBodySuspend(
        method: KSFunctionDeclaration, superCall: String, timeoutName: String, fieldTimeout: String, fieldMetric: String
    ): CodeBlock {
        val superMethod = buildMethodCall(method, superCall)
        return CodeBlock.builder().add(
            """
            try {
                  return %M(%L.timeout().toMillis()) {
                      %L
                  }
            } catch (e: %M) {
                %L?.recordTimeout(%S, %L.timeout().toNanos())
                throw %M(%S, "Timeout exceeded " + %L.timeout())
            }
          """.trimIndent(), timeoutMember, fieldTimeout, superMethod.toString(), timeoutCancelMember,
            fieldMetric, timeoutName, fieldTimeout, timeoutKoraMember, timeoutName, fieldTimeout
        ).build()
    }

    private fun buildBodyFlow(
        method: KSFunctionDeclaration, superCall: String, timeoutName: String, fieldTimeout: String, fieldMetric: String
    ): CodeBlock {
        val superMethod = buildMethodCall(method, superCall)
        return CodeBlock.builder().add(
            """
            val limit = %M()
            return %M { %M(%L) }
                .%M { limit.set(%M.nanoTime() + %L.timeout().toNanos()) }
                .%M {
                    val current = %M.nanoTime()
                    if (current > limit.get()) {
                        %L?.recordTimeout(%S, %L.timeout().toNanos())
                        throw %M(%S, "Timeout exceeded " + %L.timeout())
                    } else {
                        false
                    }
                }
            """.trimIndent(),
            atomicMember, flowMember, emitMember, superMethod.toString(), startMember, systemMember,
            fieldTimeout, whileMember, systemMember, fieldMetric, timeoutName, fieldTimeout, timeoutKoraMember, timeoutName, fieldTimeout,
        ).build()
    }

    private fun buildMethodCall(method: KSFunctionDeclaration, call: String): CodeBlock {
        return CodeBlock.of(method.parameters.asSequence().map { p -> CodeBlock.of("%L", p) }.joinToString(", ", "$call(", ")"))
    }
}
