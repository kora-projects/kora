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
import ru.tinkoff.kora.ksp.common.FunctionUtils.isSuspend
import ru.tinkoff.kora.ksp.common.exception.ProcessingErrorException
import java.util.concurrent.Future

@KspExperimental
class CachePutAopKoraAspect(private val resolver: Resolver) : AbstractAopCacheAspect() {

    private val ANNOTATION_CACHE_PUT = ClassName("ru.tinkoff.kora.cache.annotation", "CachePut")
    private val ANNOTATION_CACHE_PUTS = ClassName("ru.tinkoff.kora.cache.annotation", "CachePuts")

    override fun getSupportedAnnotationTypes(): Set<String> {
        return setOf(ANNOTATION_CACHE_PUT.canonicalName, ANNOTATION_CACHE_PUTS.canonicalName)
    }

    override fun apply(method: KSFunctionDeclaration, superCall: String, aspectContext: KoraAspect.AspectContext): KoraAspect.ApplyResult {
        if (method.isFuture()) {
            throw ProcessingErrorException("@CachePut can't be applied for types assignable from ${Future::class.java}", method)
        } else if (method.isMono()) {
            throw ProcessingErrorException("@CachePut can't be applied for types assignable from ${CommonClassNames.mono}", method)
        } else if (method.isFlux()) {
            throw ProcessingErrorException("@CachePut can't be applied for types assignable from ${CommonClassNames.flux}", method)
        }

        val operation = getCacheOperation(method, resolver, aspectContext)
        val body = if (method.isSuspend()) {
            buildBodySync(method, operation, superCall)
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
        builder.add("val _value = %L\n", superMethod)

        // cache put
        for (i in operation.executions.indices) {
            val cache = operation.executions[i]
            val keyField = "_key${i + 1}"
            builder.add("val %L = %L\n", keyField, cache.cacheKey!!.code)

            if (cache.cacheKey.type.type!!.resolve().isMarkedNullable) {
                builder.add("%L?.let { %L.put(it, _value) }\n", keyField, cache.field)
            } else {
                builder.add("%L.put(%L, _value)\n", cache.field, keyField)
            }
        }

        return builder.add("return _value").build()
    }
}
