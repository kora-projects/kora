package ru.tinkoff.kora.cache.symbol.processor.aop

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.squareup.kotlinpoet.CodeBlock
import ru.tinkoff.kora.aop.symbol.processor.KoraAspect
import ru.tinkoff.kora.cache.annotation.CacheInvalidate
import ru.tinkoff.kora.cache.annotation.CacheInvalidates
import ru.tinkoff.kora.cache.symbol.processor.CacheMeta
import ru.tinkoff.kora.cache.symbol.processor.CacheOperation
import ru.tinkoff.kora.cache.symbol.processor.CacheOperationManager.Companion.getCacheOperation
import ru.tinkoff.kora.ksp.common.FunctionUtils.isVoid

@KspExperimental
class CacheInvalidateAopKoraAspect(private val resolver: Resolver) : AbstractAopCacheAspect() {

    override fun getSupportedAnnotationTypes(): Set<String> {
        return setOf(CacheInvalidate::class.java.canonicalName, CacheInvalidates::class.java.canonicalName)
    }

    override fun apply(method: KSFunctionDeclaration, superCall: String, aspectContext: KoraAspect.AspectContext): KoraAspect.ApplyResult {
        val operation = getCacheOperation(method, resolver)
        val cacheMirrors = getCacheMirrors(operation, method, resolver)
        val cacheFields = getCacheFields(operation, cacheMirrors, aspectContext)

        val body = if (operation.meta.type == CacheMeta.Type.EVICT_ALL) {
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
        val builder = StringBuilder()

        // cache super method
        if (method.isVoid()) {
            builder.append(superMethod).append("\n")
        } else {
            builder.append("var value = ").append(superMethod).append("\n")
        }

        // cache invalidate
        for (cache in cacheFields) {
            builder.append(cache).append(".invalidate(_key)\n")
        }

        if (method.isVoid()) {
            builder.append("return")
        } else {
            builder.append("return value")
        }

        return CodeBlock.builder()
            .add("var _key = %L(%L)\n", operation.key.simpleName, recordParameters)
            .add(builder.toString())
            .build()
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
