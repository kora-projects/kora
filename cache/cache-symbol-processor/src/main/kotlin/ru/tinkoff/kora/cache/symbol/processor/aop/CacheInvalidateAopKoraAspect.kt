package ru.tinkoff.kora.cache.symbol.processor.aop

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import ru.tinkoff.kora.aop.symbol.processor.KoraAspect
import ru.tinkoff.kora.cache.symbol.processor.CacheOperation
import ru.tinkoff.kora.cache.symbol.processor.CacheOperationUtils.Companion.getCacheOperation
import ru.tinkoff.kora.ksp.common.FunctionUtils.isFlux
import ru.tinkoff.kora.ksp.common.FunctionUtils.isFuture
import ru.tinkoff.kora.ksp.common.FunctionUtils.isMono
import ru.tinkoff.kora.ksp.common.FunctionUtils.isVoid
import ru.tinkoff.kora.ksp.common.exception.ProcessingErrorException
import java.util.concurrent.Future

@KspExperimental
class CacheInvalidateAopKoraAspect(private val resolver: Resolver) : AbstractAopCacheAspect() {

    private val ANNOTATION_CACHE_INVALIDATE = ClassName("ru.tinkoff.kora.cache.annotation", "CacheInvalidate")
    private val ANNOTATION_CACHE_INVALIDATES = ClassName("ru.tinkoff.kora.cache.annotation", "CacheInvalidates")

    override fun getSupportedAnnotationTypes(): Set<String> {
        return setOf(ANNOTATION_CACHE_INVALIDATE.canonicalName, ANNOTATION_CACHE_INVALIDATES.canonicalName)
    }

    override fun apply(method: KSFunctionDeclaration, superCall: String, aspectContext: KoraAspect.AspectContext): KoraAspect.ApplyResult {
        if (method.isFuture()) {
            throw ProcessingErrorException("@CacheInvalidate can't be applied for types assignable from ${Future::class.java}", method)
        } else if (method.isMono()) {
            throw ProcessingErrorException("@CacheInvalidate can't be applied for types assignable from ${Mono::class.java}", method)
        } else if (method.isFlux()) {
            throw ProcessingErrorException("@CacheInvalidate can't be applied for types assignable from ${Flux::class.java}", method)
        }

        val operation = getCacheOperation(method)
        val cacheFields = getCacheFields(operation, resolver, aspectContext)

        val body = if (operation.type == CacheOperation.Type.EVICT_ALL) {
            buildBodySyncAll(method, operation, superCall, cacheFields)
        } else {
            buildBodySync(method, operation, superCall, cacheFields)
        }

        return KoraAspect.ApplyResult.MethodBody(body)
    }

    private fun buildBodySync(
        method: KSFunctionDeclaration,
        operation: CacheOperation,
        superCall: String,
        cacheFields: List<String>
    ): CodeBlock {
        val recordParameters = getKeyRecordParameters(operation, method)
        val superMethod = getSuperMethod(method, superCall)
        val builder = CodeBlock.builder()
        val isSingleNullableParam = operation.parameters.size == 1 && operation.parameters[0].type.resolve().isMarkedNullable

        // cache super method
        if (method.isVoid()) {
            builder.add(superMethod).add("\n")
        } else {
            builder.add("var value = %L\n", superMethod)
        }

        // cache invalidate
        for (cache in cacheFields) {
            if (isSingleNullableParam) {
                builder.add("_key?.let { %L.invalidate(it) }\n", cache)
            } else {
                builder.add("%L.invalidate(_key)\n", cache)
            }
        }

        if (method.isVoid()) {
            builder.add("return")
        } else {
            builder.add("return value")
        }

        return if (operation.parameters.size == 1) {
            CodeBlock.builder()
                .add("val _key = %L\n", operation.parameters[0])
                .add(builder.build())
                .build()
        } else {
            CodeBlock.builder()
                .add("val _key = %T.of(%L)\n", getCacheKey(operation), recordParameters)
                .add(builder.build())
                .build()
        }
    }

    private fun buildBodySyncAll(
        method: KSFunctionDeclaration,
        operation: CacheOperation,
        superCall: String,
        cacheFields: List<String>
    ): CodeBlock {
        val superMethod = getSuperMethod(method, superCall)
        val builder = StringBuilder()

        // cache super method
        if (method.isVoid()) {
            builder.append(superMethod).append("\n")
        } else {
            builder.append("var _value = ").append(superMethod).append("\n")
        }

        // cache invalidate
        for (cache in cacheFields) {
            builder.append(cache).append(".invalidateAll()\n")
        }

        if (method.isVoid()) {
            builder.append("return")
        } else {
            builder.append("return _value")
        }

        return CodeBlock.builder()
            .add(builder.toString())
            .build()
    }
}
