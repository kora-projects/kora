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
            throw ProcessingErrorException("@CachePut can't be applied for types assignable from ${Mono::class.java}", method)
        } else if (method.isFlux()) {
            throw ProcessingErrorException("@CachePut can't be applied for types assignable from ${Flux::class.java}", method)
        }

        val operation = getCacheOperation(method)
        val fieldManagers = getCacheFields(operation, resolver, aspectContext)

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
        val builder = CodeBlock.builder()
        val isSingleNullableParam = operation.parameters.size == 1 && operation.parameters[0].type.resolve().isMarkedNullable

        // cache super method
        builder.add("val _value = ").add(superMethod).add("\n")

        if (operation.parameters.size == 1) {
            builder.add("val _key = %L\n", operation.parameters[0])
        } else {
            builder.add("val _key = %T.of(%L)\n", getCacheKey(operation), recordParameters)
        }

        // cache put
        for (cache in cacheFields) {
            if (isSingleNullableParam) {
                builder.add("_key?.let { %L.put(it, _value) }\n", cache)
            } else {
                builder.add("%L.put(_key, _value)\n", cache)
            }
        }

        return builder.add("return _value").build()
    }
}
