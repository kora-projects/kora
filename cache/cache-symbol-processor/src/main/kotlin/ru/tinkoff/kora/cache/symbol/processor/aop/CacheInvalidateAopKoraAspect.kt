package ru.tinkoff.kora.cache.symbol.processor.aop

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import ru.tinkoff.kora.aop.symbol.processor.KoraAspect
import ru.tinkoff.kora.cache.symbol.processor.CacheOperation
import ru.tinkoff.kora.cache.symbol.processor.CacheOperationUtils.Companion.getCacheOperation
import ru.tinkoff.kora.ksp.common.CommonClassNames
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
            throw ProcessingErrorException("@CacheInvalidate can't be applied for types assignable from ${CommonClassNames.mono}", method)
        } else if (method.isFlux()) {
            throw ProcessingErrorException("@CacheInvalidate can't be applied for types assignable from ${CommonClassNames.flux}", method)
        }

        val operation = getCacheOperation(method, resolver, aspectContext)
        val body = if (operation.type == CacheOperation.Type.EVICT_ALL) {
            buildBodySyncAll(method, operation, superCall)
        } else {
            buildBodySync(method, operation, superCall)
        }

        return KoraAspect.ApplyResult.MethodBody(body)
    }

    private fun buildBodySync(
        method: KSFunctionDeclaration,
        operation: CacheOperation,
        superCall: String,
    ): CodeBlock {
        val superMethod = getSuperMethod(method, superCall)
        val builder = CodeBlock.builder()

        // cache super method
        if (method.isVoid()) {
            builder.add(superMethod).add("\n")
        } else {
            builder.add("var value = %L\n", superMethod)
        }

        // cache invalidate
        for (i in operation.executions.indices) {
            val cache = operation.executions[i]
            val keyField = "_key${i + 1}"
            builder.add("val %L = %L\n", keyField, cache.cacheKey!!.code)

            if (cache.cacheKey.type.type!!.resolve().isMarkedNullable) {
                builder.add("%L?.let { %L.invalidate(it) }\n", keyField, cache.field)
            } else {
                builder.add("%L.invalidate(%L)\n", cache.field, keyField)
            }
        }

        if (method.isVoid()) {
            builder.add("return")
        } else {
            builder.add("return value")
        }

        return CodeBlock.builder()
            .add(builder.build())
            .build()
    }

    private fun buildBodySyncAll(
        method: KSFunctionDeclaration,
        operation: CacheOperation,
        superCall: String,
    ): CodeBlock {
        val superMethod = getSuperMethod(method, superCall)
        val builder = CodeBlock.builder()

        // cache super method
        if (method.isVoid()) {
            builder.add(superMethod).add("\n")
        } else {
            builder.add("var _value = %L\n", superMethod)
        }

        // cache invalidate
        for (cache in operation.executions) {
            builder.add(cache.field).add(".invalidateAll()\n")
        }

        if (method.isVoid()) {
            builder.add("return")
        } else {
            builder.add("return _value")
        }

        return builder.build()
    }
}
