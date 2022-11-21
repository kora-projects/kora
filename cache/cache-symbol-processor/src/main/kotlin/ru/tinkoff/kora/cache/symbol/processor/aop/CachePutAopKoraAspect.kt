package ru.tinkoff.kora.cache.symbol.processor.aop

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.squareup.kotlinpoet.CodeBlock
import ru.tinkoff.kora.aop.symbol.processor.KoraAspect
import ru.tinkoff.kora.cache.annotation.CachePut
import ru.tinkoff.kora.cache.annotation.CachePuts
import ru.tinkoff.kora.cache.symbol.processor.CacheOperation
import ru.tinkoff.kora.cache.symbol.processor.CacheOperationManager.Companion.getCacheOperation
import ru.tinkoff.kora.ksp.common.FunctionUtils.isSuspend

@KspExperimental
class CachePutAopKoraAspect(private val resolver: Resolver) : AbstractAopCacheAspect() {

    override fun getSupportedAnnotationTypes(): Set<String> {
        return setOf(CachePut::class.java.canonicalName, CachePuts::class.java.canonicalName)
    }

    override fun apply(method: KSFunctionDeclaration, superCall: String, aspectContext: KoraAspect.AspectContext): KoraAspect.ApplyResult {
        val operation = getCacheOperation(method, resolver)
        val cacheMirrors = getCacheMirrors(operation, method, resolver)
        val fieldManagers = getCacheFields(operation, cacheMirrors, aspectContext)

        val body = if (method.isSuspend()) {
            buildBodySync(method, operation, superCall, fieldManagers)
        } else {
            buildBodySync(method, operation, superCall, fieldManagers)
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
        builder.append("var _value = ").append(superMethod).append("\n")

        // cache put
        for (cache in cacheFields) {
            builder.append(cache).append(".put(_key, _value)\n")
        }
        builder.append("return _value")

        return CodeBlock.builder()
            .add("var _key = %L(%L)\n", operation.key.simpleName, recordParameters)
            .add(builder.toString())
            .build()
    }
}
