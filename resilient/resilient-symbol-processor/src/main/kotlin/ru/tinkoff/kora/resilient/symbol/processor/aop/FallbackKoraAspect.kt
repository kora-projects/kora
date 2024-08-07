package ru.tinkoff.kora.resilient.symbol.processor.aop

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.MemberName
import ru.tinkoff.kora.aop.symbol.processor.KoraAspect
import ru.tinkoff.kora.ksp.common.AnnotationUtils.findAnnotations
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

class FallbackKoraAspect(val resolver: Resolver) : KoraAspect {

    companion object {
        private val ANNOTATION_TYPE = ClassName("ru.tinkoff.kora.resilient.fallback.annotation", "Fallback")
    }

    override fun getSupportedAnnotationTypes(): Set<String> {
        return setOf(ANNOTATION_TYPE.canonicalName)
    }

    override fun apply(method: KSFunctionDeclaration, superCall: String, aspectContext: KoraAspect.AspectContext): KoraAspect.ApplyResult {
        if (method.isFuture()) {
            throw ProcessingErrorException("@Fallback can't be applied for types assignable from ${Future::class.java}", method)
        } else if (method.isCompletionStage()) {
            throw ProcessingErrorException("@Fallback can't be applied for types assignable from ${CompletionStage::class.java}", method)
        } else if (method.isMono()) {
            throw ProcessingErrorException("@Fallback can't be applied for types assignable from ${CommonClassNames.mono}", method)
        } else if (method.isFlux()) {
            throw ProcessingErrorException("@Fallback can't be applied for types assignable from ${CommonClassNames.flux}", method)
        }

        val annotation = method.findAnnotations(ANNOTATION_TYPE).first()

        val fallbackName = annotation.findValue<String>("value")!!
        val fallback = annotation.asFallback(method)

        val managerType = resolver.getClassDeclarationByName("ru.tinkoff.kora.resilient.fallback.FallbackManager")!!.asType(listOf())
        val fieldManager = aspectContext.fieldFactory.constructorParam(managerType, listOf())
        val fallbackType = resolver.getClassDeclarationByName("ru.tinkoff.kora.resilient.fallback.Fallback")!!.asType(listOf())
        val fieldFallback = aspectContext.fieldFactory.constructorInitialized(
            fallbackType,
            CodeBlock.of("%L[%S]", fieldManager, fallbackName)
        )

        val body = if (method.isFlow()) {
            buildBodyFlow(method, fallback, superCall, fieldFallback)
        } else if (method.isSuspend()) {
            buildBodySync(method, fallback, superCall, fieldFallback)
        } else {
            buildBodySync(method, fallback, superCall, fieldFallback)
        }

        return KoraAspect.ApplyResult.MethodBody(body)
    }

    private fun buildBodySync(
        method: KSFunctionDeclaration, fallbackCall: FallbackMeta, superCall: String, fieldFallback: String
    ): CodeBlock {
        val prefix = if (method.isVoid()) "" else "return "
        val superMethod = buildMethodCall(method, superCall)
        return CodeBlock.builder().add(
            """
            ${prefix}try {
                %L
            } catch (_e: Throwable) {
                if(%L.canFallback(_e)) {
                    %L
                } else {
                    throw _e
                }
            }
            """.trimIndent(), superMethod.toString(), fieldFallback, fallbackCall.call()
        ).build()
    }

    private fun buildBodyFlow(
        method: KSFunctionDeclaration, fallbackCall: FallbackMeta, superCall: String, fieldFallback: String
    ): CodeBlock {
        val flowMember = MemberName("kotlinx.coroutines.flow", "flow")
        val catchMember = MemberName("kotlinx.coroutines.flow", "catch")
        val emitMember = MemberName("kotlinx.coroutines.flow", "emitAll")
        val superMethod = buildMethodCall(method, superCall)
        return CodeBlock.builder().add(
            """
            return %M {
                %M(%L)
            }.%M { _e ->
                if (%L.canFallback(_e)) {
                    %M(%L)
                } else {
                    throw _e
                }
            }
            """.trimIndent(), flowMember, emitMember, superMethod.toString(), catchMember, fieldFallback, emitMember, fallbackCall.call()
        ).build()
    }

    private fun buildMethodCall(method: KSFunctionDeclaration, call: String): CodeBlock {
        return CodeBlock.of(method.parameters.asSequence().map { p -> CodeBlock.of("%L", p) }.joinToString(", ", "$call(", ")"))
    }

    private fun buildMethodCallable(method: KSFunctionDeclaration, call: String): CodeBlock {
        val callableMember = MemberName("java.util.concurrent", "Callable")
        return CodeBlock.builder().add("%M { %L }", callableMember, buildMethodCall(method, call)).build()
    }
}
