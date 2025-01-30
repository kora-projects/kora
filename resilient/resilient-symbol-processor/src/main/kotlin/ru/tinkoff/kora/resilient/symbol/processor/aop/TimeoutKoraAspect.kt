package ru.tinkoff.kora.resilient.symbol.processor.aop

import com.google.devtools.ksp.KspExperimental
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

@KspExperimental
class TimeoutKoraAspect(val resolver: Resolver) : KoraAspect {

    companion object {
        private val ANNOTATION_TYPE = ClassName("ru.tinkoff.kora.resilient.timeout.annotation", "Timeout")
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
        return setOf(ANNOTATION_TYPE.canonicalName)
    }

    override fun apply(ksFunction: KSFunctionDeclaration, superCall: String, aspectContext: KoraAspect.AspectContext): KoraAspect.ApplyResult {
        if (ksFunction.isFuture()) {
            throw ProcessingErrorException("@Timeout can't be applied for types assignable from ${Future::class.java}", ksFunction)
        } else if (ksFunction.isCompletionStage()) {
            throw ProcessingErrorException("@Timeout can't be applied for types assignable from ${CompletionStage::class.java}", ksFunction)
        } else if (ksFunction.isMono()) {
            throw ProcessingErrorException("@Timeout can't be applied for types assignable from ${CommonClassNames.mono}", ksFunction)
        } else if (ksFunction.isFlux()) {
            throw ProcessingErrorException("@Timeout can't be applied for types assignable from ${CommonClassNames.flux}", ksFunction)
        }

        val annotation = ksFunction.findAnnotation(ANNOTATION_TYPE)!!

        val timeoutName = annotation.findValue<String>("value")!!

        val metricType = resolver.getClassDeclarationByName("ru.tinkoff.kora.resilient.timeout.TimeoutMetrics")!!.asType(listOf()).makeNullable()
        val fieldMetric = aspectContext.fieldFactory.constructorParam(metricType, listOf())
        val managerType = resolver.getClassDeclarationByName("ru.tinkoff.kora.resilient.timeout.TimeoutManager")!!.asType(listOf())
        val fieldManager = aspectContext.fieldFactory.constructorParam(managerType, listOf())
        val fieldTimeout = aspectContext.fieldFactory.constructorInitialized(
            resolver.getClassDeclarationByName("ru.tinkoff.kora.resilient.timeout.Timeout")!!.asType(listOf()),
            CodeBlock.of("%L[%S]", fieldManager, timeoutName)
        )

        val body = if (ksFunction.isFlow()) {
            buildBodyFlow(ksFunction, superCall, timeoutName, fieldTimeout, fieldMetric)
        } else if (ksFunction.isSuspend()) {
            buildBodySuspend(ksFunction, superCall, timeoutName, fieldTimeout, fieldMetric)
        } else {
            buildBodySync(ksFunction, superCall, fieldTimeout)
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
