package io.koraframework.resilient.symbol.processor.aop

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.MemberName
import io.koraframework.aop.symbol.processor.KoraAspect
import io.koraframework.ksp.common.AnnotationUtils.findAnnotations
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

class FallbackKoraAspect(val resolver: Resolver) : KoraAspect {

    companion object {
        private val ANNOTATION_TYPE = ClassName("io.koraframework.resilient.fallback.annotation", "Fallback")
        private val FALLBACK_TELEMETRY = ClassName("io.koraframework.resilient.fallback.telemetry", "FallbackTelemetry")
        private val FALLBACK_TELEMETRY_FACTORY = ClassName("io.koraframework.resilient.fallback.telemetry", "FallbackTelemetryFactory")
        private val RESILIENT_CONFIG = ClassName("io.koraframework.resilient", "ResilientConfig")
    }

    override fun getSupportedAnnotationTypes(): Set<String> {
        return setOf(ANNOTATION_TYPE.canonicalName)
    }

    override fun apply(ksFunction: KSFunctionDeclaration, superCall: String, aspectContext: KoraAspect.AspectContext): KoraAspect.ApplyResult {
        if (ksFunction.isFuture()) {
            throw ProcessingErrorException("@Fallback can't be applied for types assignable from ${Future::class.java}", ksFunction)
        } else if (ksFunction.isCompletionStage()) {
            throw ProcessingErrorException("@Fallback can't be applied for types assignable from ${CompletionStage::class.java}", ksFunction)
        } else if (ksFunction.isMono()) {
            throw ProcessingErrorException("@Fallback can't be applied for types assignable from ${CommonClassNames.mono}", ksFunction)
        } else if (ksFunction.isFlux()) {
            throw ProcessingErrorException("@Fallback can't be applied for types assignable from ${CommonClassNames.flux}", ksFunction)
        }

        val annotation = ksFunction.findAnnotations(ANNOTATION_TYPE).first()

        val fallback = annotation.asFallback(ksFunction)
        val telemetryName = "${ksFunction.parentDeclaration?.qualifiedName?.asString()}.${ksFunction.simpleName.asString()}"
        val fieldTelemetryFactory = aspectContext.fieldFactory.constructorParam(FALLBACK_TELEMETRY_FACTORY, listOf())
        val fieldResilientConfig = aspectContext.fieldFactory.constructorParam(RESILIENT_CONFIG, listOf())
        val fieldTelemetry = aspectContext.fieldFactory.constructorInitialized(
            FALLBACK_TELEMETRY,
            CodeBlock.of("%N.get(%S, %N.fallback())", fieldTelemetryFactory, telemetryName, fieldResilientConfig)
        )

        val body = if (ksFunction.isFlow()) {
            buildBodyFlow(ksFunction, fallback, superCall, fieldTelemetry)
        } else {
            buildBodySync(ksFunction, fallback, superCall, fieldTelemetry)
        }

        return KoraAspect.ApplyResult.MethodBody(body)
    }

    private fun buildBodySync(
        method: KSFunctionDeclaration, fallbackCall: FallbackMeta, superCall: String, fieldTelemetry: String
    ): CodeBlock {
        val prefix = if (method.isVoid()) "" else "return "
        val superMethod = buildMethodCall(method, superCall)
        val reasonGuard = fallbackCall.reasonTypeName()
            ?.let { CodeBlock.of("if (_e !is %T) throw _e\n", it) }
            ?: CodeBlock.of("")
        return CodeBlock.builder().add(
            """
            ${prefix}try {
                %L
            } catch (_e: Throwable) {
                %L
                val _fallbackObservation = %L.observe()
                try {
                    _fallbackObservation.recordExecute(_e)
                    %L
                } catch (_fallbackException: Throwable) {
                    _fallbackObservation.observeError(_fallbackException)
                    throw _fallbackException
                } finally {
                    _fallbackObservation.end()
                }
            }
            """.trimIndent(), superMethod.toString(), reasonGuard, fieldTelemetry, fallbackCall.call()
        ).build()
    }

    private fun buildBodyFlow(
        method: KSFunctionDeclaration, fallbackCall: FallbackMeta, superCall: String, fieldTelemetry: String
    ): CodeBlock {
        val flowMember = MemberName("kotlinx.coroutines.flow", "flow")
        val catchMember = MemberName("kotlinx.coroutines.flow", "catch")
        val emitMember = MemberName("kotlinx.coroutines.flow", "emitAll")
        val superMethod = buildMethodCall(method, superCall)
        val reasonGuard = fallbackCall.reasonTypeName()
            ?.let { CodeBlock.of("if (_e !is %T) throw _e\n", it) }
            ?: CodeBlock.of("")
        return CodeBlock.builder().add(
            """
            return %M {
                %M(%L)
            }.%M { _e ->
                %L
                val _fallbackObservation = %L.observe()
                try {
                    _fallbackObservation.recordExecute(_e)
                    %M(%L)
                } catch (_fallbackException: Throwable) {
                    _fallbackObservation.observeError(_fallbackException)
                    throw _fallbackException
                } finally {
                    _fallbackObservation.end()
                }
            }
            """.trimIndent(), flowMember, emitMember, superMethod.toString(), catchMember, reasonGuard, fieldTelemetry, emitMember, fallbackCall.call()
        ).build()
    }

    private fun buildMethodCall(method: KSFunctionDeclaration, call: String): CodeBlock {
        return CodeBlock.of(method.parameters.asSequence().map { p -> CodeBlock.of("%L", p) }.joinToString(", ", "$call(", ")"))
    }

}
