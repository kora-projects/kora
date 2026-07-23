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
import io.koraframework.ksp.common.FunctionUtils.isFlow
import io.koraframework.ksp.common.FunctionUtils.isFlux
import io.koraframework.ksp.common.FunctionUtils.isCompletionStage
import io.koraframework.ksp.common.FunctionUtils.isFuture
import io.koraframework.ksp.common.FunctionUtils.isMono
import io.koraframework.ksp.common.FunctionUtils.isSuspend
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
        val timeoutMember = MemberName("kotlinx.coroutines", "withTimeout")
        val timeoutCancelMember = MemberName("kotlinx.coroutines", "TimeoutCancellationException")
        val flowMember = MemberName("kotlinx.coroutines.flow", "flow")
        val emitMember = MemberName("kotlinx.coroutines.flow", "emitAll")
        val startMember = MemberName("kotlinx.coroutines.flow", "onStart")
        val whileMember = MemberName("kotlinx.coroutines.flow", "takeWhile")
        val systemMember = MemberName("java.lang", "System")
        val atomicMember = MemberName("java.util.concurrent.atomic", "AtomicLong")
        val timeoutKoraMember = MemberName("io.koraframework.resilient.timeout.exception", "TimeoutExhaustedException")
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

        val timeoutType = ksFunction.findAnnotation(ANNOTATION_TYPE)!!
            .findValue<KSType>("value")!!
        val baseTimeout = resolver.getClassDeclarationByName(TIMEOUT.canonicalName)!!.asStarProjectedType()
        if (!baseTimeout.isAssignableFrom(timeoutType)) {
            throw ProcessingErrorException("@Timeout value must extend ${TIMEOUT.canonicalName}", ksFunction)
        }
        val timeoutName = timeoutType.declaration.simpleName.asString()
        val fieldTimeout = aspectContext.fieldFactory.constructorParam(
            timeoutType.toTypeName(),
            listOf()
        )

        val body = if (ksFunction.isFlow()) {
            buildBodyFlow(ksFunction, superCall, timeoutName, fieldTimeout)
        } else if (ksFunction.isSuspend()) {
            buildBodySuspend(ksFunction, superCall, timeoutName, fieldTimeout)
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
                    %L.execute(io.koraframework.resilient.timeout.Timeouter.TimeoutRunnable { %L })
                    """.trimIndent(), timeoutName, superMethod.toString()
            ).build()
        } else {
            CodeBlock.builder().add(
                """
                    return %L.execute(io.koraframework.resilient.timeout.Timeouter.TimeoutCallable { %L })
                    """.trimIndent(), timeoutName, superMethod.toString()
            ).build()
        }
    }

    private fun buildBodySuspend(
        method: KSFunctionDeclaration, superCall: String, timeoutName: String, fieldTimeout: String
    ): CodeBlock {
        val superMethod = buildMethodCall(method, superCall)
        return CodeBlock.builder().add(
            """
            try {
                return %M(%L.timeout().toMillis()) {
                    %L
                }
            } catch (e: %M) {
                throw %M(%S, "Timeout exceeded " + %L.timeout())
            }
          """.trimIndent(), timeoutMember, fieldTimeout, superMethod.toString(), timeoutCancelMember,
            timeoutKoraMember, timeoutName, fieldTimeout
        ).build()
    }

    private fun buildBodyFlow(
        method: KSFunctionDeclaration, superCall: String, timeoutName: String, fieldTimeout: String
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
                        throw %M(%S, "Timeout exceeded " + %L.timeout())
                    } else {
                        false
                    }
                }
            """.trimIndent(),
            atomicMember, flowMember, emitMember, superMethod.toString(), startMember, systemMember,
            fieldTimeout, whileMember, systemMember, timeoutKoraMember, timeoutName, fieldTimeout,
        ).build()
    }

    private fun buildMethodCall(method: KSFunctionDeclaration, call: String): CodeBlock {
        return CodeBlock.of(method.parameters.asSequence().map { p -> CodeBlock.of("%L", p) }.joinToString(", ", "$call(", ")"))
    }
}
