package io.koraframework.resilient.symbol.processor.aop

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.MemberName
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
import io.koraframework.ksp.common.KotlinPoetUtils.controlFlow
import io.koraframework.ksp.common.exception.ProcessingErrorException
import java.util.concurrent.CompletionStage
import java.util.concurrent.Future

@KspExperimental
class RetryKoraAspect(val resolver: Resolver) : KoraAspect {

    companion object {
        private val ANNOTATION_TYPE = ClassName("io.koraframework.resilient.retry.annotation", "Retry")

        private val RETRY_RUNNER = ClassName("io.koraframework.resilient.retry", "Retry", "RetryRunnable")
        private val RETRY_SUPPLIER = ClassName("io.koraframework.resilient.retry", "Retry", "RetrySupplier")
        private val MEMBER_RETRY_STATUS = ClassName("io.koraframework.resilient.retry", "Retry", "RetryState", "RetryStatus")
        private val MEMBER_RETRY_EXCEPTION = MemberName("io.koraframework.resilient.retry", "RetryExhaustedException")
        private val MEMBER_DELAY = MemberName("kotlinx.coroutines", "delay")
        private val MEMBER_TIME = MemberName("kotlin.time.Duration.Companion", "nanoseconds")
        private val MEMBER_FLOW = MemberName("kotlinx.coroutines.flow", "flow")
        private val MEMBER_FLOW_EMIT = MemberName("kotlinx.coroutines.flow", "emitAll")
        private val MEMBER_FLOW_RETRY = MemberName("kotlinx.coroutines.flow", "retryWhen")
    }

    override fun getSupportedAnnotationTypes(): Set<String> {
        return setOf(ANNOTATION_TYPE.canonicalName)
    }

    override fun apply(ksFunction: KSFunctionDeclaration, superCall: String, aspectContext: KoraAspect.AspectContext): KoraAspect.ApplyResult {
        if (ksFunction.isFuture()) {
            throw ProcessingErrorException("@Retryable can't be applied for types assignable from ${Future::class.java}", ksFunction)
        } else if (ksFunction.isCompletionStage()) {
            throw ProcessingErrorException("@Retryable can't be applied for types assignable from ${CompletionStage::class.java}", ksFunction)
        } else if (ksFunction.isMono()) {
            throw ProcessingErrorException("@Retryable can't be applied for types assignable from ${CommonClassNames.mono}", ksFunction)
        } else if (ksFunction.isFlux()) {
            throw ProcessingErrorException("@Retryable can't be applied for types assignable from ${CommonClassNames.flux}", ksFunction)
        }

        val retryableName = ksFunction.findAnnotation(ANNOTATION_TYPE)!!
            .findValue<String>("value")!!

        val managerType = resolver.getClassDeclarationByName("io.koraframework.resilient.retry.RetryManager")!!.asType(listOf())
        val fieldManager = aspectContext.fieldFactory.constructorParam(managerType, listOf())
        val retrierType = resolver.getClassDeclarationByName("io.koraframework.resilient.retry.Retry")!!.asType(listOf())
        val fieldRetrier = aspectContext.fieldFactory.constructorInitialized(
            retrierType,
            CodeBlock.of("%L[%S]", fieldManager, retryableName)
        )

        val body = if (ksFunction.isFlow()) {
            val retryConfigType = resolver.getClassDeclarationByName("io.koraframework.resilient.retry.RetryConfig")!!.asType(listOf())
            val fieldRetryConfig = aspectContext.fieldFactory.constructorParam(retryConfigType, listOf())
            val retryConfigNamedType = resolver.getClassDeclarationByName("io.koraframework.resilient.retry.RetryConfig.NamedConfig")!!.asType(listOf())
            val fieldRetryNamedConfig = aspectContext.fieldFactory.constructorInitialized(
                retryConfigNamedType,
                CodeBlock.of("%L.getNamedConfig(%S)", fieldRetryConfig, retryableName)
            )
            buildBodyFlow(ksFunction, superCall, fieldRetrier, fieldRetryNamedConfig, retryableName)
        } else if (ksFunction.isSuspend()) {
            val retryConfigType = resolver.getClassDeclarationByName("io.koraframework.resilient.retry.RetryConfig")!!.asType(listOf())
            val fieldRetryConfig = aspectContext.fieldFactory.constructorParam(retryConfigType, listOf())
            val retryConfigNamedType = resolver.getClassDeclarationByName("io.koraframework.resilient.retry.RetryConfig.NamedConfig")!!.asType(listOf())
            val fieldRetryNamedConfig = aspectContext.fieldFactory.constructorInitialized(
                retryConfigNamedType,
                CodeBlock.of("%L.getNamedConfig(%S)", fieldRetryConfig, retryableName)
            )
            buildBodySuspend(ksFunction, superCall, fieldRetrier, fieldRetryNamedConfig, retryableName)
        } else {
            buildBodySync(ksFunction, superCall, fieldRetrier)
        }

        return KoraAspect.ApplyResult.MethodBody(body)
    }

    private fun buildBodySync(method: KSFunctionDeclaration, superCall: String, fieldRetrier: String): CodeBlock {
        val builder = CodeBlock.builder()

        if (method.isVoid()) {
            builder.addStatement("%L.retry(%T { %L })", fieldRetrier, RETRY_RUNNER, buildMethodCall(method, superCall))
        } else {
            builder.addStatement("return %L.retry(%T { %L })", fieldRetrier, RETRY_SUPPLIER, buildMethodCall(method, superCall))
        }

        return builder.build()
    }

    private fun buildBodySuspend(
        method: KSFunctionDeclaration,
        superCall: String,
        fieldRetrier: String,
        fieldRetryNamedConfig: String,
        retryableName: String
    ): CodeBlock {
        return CodeBlock.builder()
            .controlFlow("if (java.lang.Boolean.FALSE.equals(%L.enabled()))", fieldRetryNamedConfig) {
                addStatement("return " + buildMethodCall(method, superCall).toString())
            }
            .add("%L.asState()", fieldRetrier).indent().add("\n")
            .controlFlow(".use { _state ->", fieldRetrier) {
                beginControlFlow("if (_state.attemptsMax == 0)")
                addStatement("return " + buildMethodCall(method, superCall).toString())
                nextControlFlow("else")
                addStatement("val _suppressed = %T<Exception>()", ArrayList::class)
                controlFlow("while (true)") {
                    controlFlow("try") {
                        add("return ").add(buildMethodCall(method, superCall)).add("\n")
                        nextControlFlow("catch (_e: Exception)")
                        addStatement("val _status = _state.onException(_e)")
                        controlFlow("when (_status)") {
                            controlFlow("%T.REJECTED ->", MEMBER_RETRY_STATUS) {
                                addStatement("_suppressed.forEach { _e.addSuppressed(it) }")
                                addStatement("throw _e")
                            }
                            controlFlow("%T.ACCEPTED ->", MEMBER_RETRY_STATUS) {
                                addStatement("_suppressed.add(_e)")
                                if (method.isSuspend()) {
                                    addStatement("%M(_state.delayNanos.%M)", MEMBER_DELAY, MEMBER_TIME)
                                } else {
                                    addStatement("_state.doDelay()")
                                }
                            }
                            controlFlow("%T.EXHAUSTED ->", MEMBER_RETRY_STATUS) {
                                add(
                                    """
                                    val _exhaustedException = %M(%S, _state.getAttempts(), _e)
                                    _suppressed.forEach { _e.addSuppressed(it) }
                                    throw _exhaustedException
                                    
                                    """.trimIndent(), MEMBER_RETRY_EXCEPTION, retryableName
                                )
                            }
                        }
                    }
                }
                endControlFlow()
            }
            .unindent()
            .build()
    }

    private fun buildBodyFlow(
        method: KSFunctionDeclaration,
        superCall: String,
        fieldRetrier: String,
        fieldRetryNamedConfig: String,
        retryableName: String
    ): CodeBlock {
        return CodeBlock.builder()
            .controlFlow("if (java.lang.Boolean.FALSE.equals(%L.enabled()))", fieldRetryNamedConfig) {
                addStatement("return " + buildMethodCall(method, superCall).toString())
            }
            .controlFlow("return %M", MEMBER_FLOW) {
                controlFlow("return@flow %L.asState().use { _state ->", fieldRetrier) {
                    beginControlFlow("if (_state.attemptsMax == 0)")
                    addStatement("%M (%L)", MEMBER_FLOW_EMIT, buildMethodCall(method, superCall).toString())
                    nextControlFlow("else")
                    add("%M (", MEMBER_FLOW_EMIT).indent()
                    add(buildMethodCall(method, superCall)).add(".").controlFlow("%M { _cause, _ ->", MEMBER_FLOW_RETRY) {
                        addStatement("val _status = _state.onException(_cause)")
                        controlFlow("when (_status)") {
                            addStatement("%T.REJECTED -> false", MEMBER_RETRY_STATUS)
                            controlFlow("%T.ACCEPTED ->", MEMBER_RETRY_STATUS) {
                                addStatement("%M(_state.delayNanos.%M)", MEMBER_DELAY, MEMBER_TIME)
                                addStatement("true")
                            }
                            controlFlow("%T.EXHAUSTED ->", MEMBER_RETRY_STATUS) {
                                addStatement("throw %M(%S, _state.getAttempts(), _cause)", MEMBER_RETRY_EXCEPTION, retryableName)
                            }
                        }
                    }
                    unindent().add(")\n")
                    endControlFlow()
                }
            }.build()
    }

    private fun buildMethodCall(method: KSFunctionDeclaration, call: String): CodeBlock {
        return CodeBlock.of(method.parameters.asSequence().map { p -> CodeBlock.of("%L", p) }.joinToString(", ", "$call(", ")"))
    }
}
