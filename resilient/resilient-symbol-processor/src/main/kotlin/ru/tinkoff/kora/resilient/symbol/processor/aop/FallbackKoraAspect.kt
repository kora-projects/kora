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
class FallbackKoraAspect(val resolver: Resolver) : KoraAspect {

    companion object {
        const val ANNOTATION_TYPE: String = "ru.tinkoff.kora.resilient.fallback.annotation.Fallback"
    }

    override fun getSupportedAnnotationTypes(): Set<String> {
        return setOf(ANNOTATION_TYPE)
    }

    override fun apply(method: KSFunctionDeclaration, superCall: String, aspectContext: KoraAspect.AspectContext): KoraAspect.ApplyResult {
        if (method.isFuture()) {
            throw ProcessingErrorException("@Fallback can't be applied for types assignable from ${Future::class.java}", method)
        } else if (method.isMono()) {
            throw ProcessingErrorException("@Fallback can't be applied for types assignable from ${Mono::class.java}", method)
        } else if (method.isFlux()) {
            throw ProcessingErrorException("@Fallback can't be applied for types assignable from ${Flux::class.java}", method)
        }

        val annotation = method.annotations.filter { a -> a.annotationType.resolve().toClassName().canonicalName == ANNOTATION_TYPE }.first()
        val fallbackName = annotation.arguments.asSequence().filter { arg -> arg.name!!.getShortName() == "value" }.map { arg -> arg.value.toString() }.first()
        val fallback = annotation.asFallback(method)

        val managerType = resolver.getClassDeclarationByName("ru.tinkoff.kora.resilient.fallback.FallbackerManager")!!.asType(listOf())
        val fieldManager = aspectContext.fieldFactory.constructorParam(managerType, listOf())
        val fallbackType = resolver.getClassDeclarationByName("ru.tinkoff.kora.resilient.fallback.Fallbacker")!!.asType(listOf())
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
            } catch (e: Throwable) {
                if(%L.canFallback(e)) {
                    %L
                } else {
                    throw e
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
            }.%M { e ->
                if (%L.canFallback(e)) {
                    %M(%L)
                } else {
                    throw e
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
