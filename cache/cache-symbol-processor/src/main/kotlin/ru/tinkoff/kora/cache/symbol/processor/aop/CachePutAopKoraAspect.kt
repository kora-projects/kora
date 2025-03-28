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
import ru.tinkoff.kora.ksp.common.FunctionUtils.isCompletionStage
import ru.tinkoff.kora.ksp.common.FunctionUtils.isFuture
import ru.tinkoff.kora.ksp.common.FunctionUtils.isMono
import ru.tinkoff.kora.ksp.common.FunctionUtils.isSuspend
import ru.tinkoff.kora.ksp.common.FunctionUtils.isVoid
import ru.tinkoff.kora.ksp.common.exception.ProcessingErrorException
import java.util.concurrent.CompletionStage
import java.util.concurrent.Future

@KspExperimental
class CachePutAopKoraAspect(private val resolver: Resolver) : AbstractAopCacheAspect() {

    private val ANNOTATION_CACHE_PUT = ClassName("ru.tinkoff.kora.cache.annotation", "CachePut")
    private val ANNOTATION_CACHE_PUTS = ClassName("ru.tinkoff.kora.cache.annotation", "CachePuts")

    override fun getSupportedAnnotationTypes(): Set<String> {
        return setOf(ANNOTATION_CACHE_PUT.canonicalName, ANNOTATION_CACHE_PUTS.canonicalName)
    }

    override fun apply(ksFunction: KSFunctionDeclaration, superCall: String, aspectContext: KoraAspect.AspectContext): KoraAspect.ApplyResult {
        if (ksFunction.isFuture()) {
            throw ProcessingErrorException("@CachePut can't be applied for types assignable from ${Future::class.java}", ksFunction)
        } else if (ksFunction.isCompletionStage()) {
            throw ProcessingErrorException("@CachePut can't be applied for types assignable from ${CompletionStage::class.java}", ksFunction)
        } else if (ksFunction.isMono()) {
            throw ProcessingErrorException("@CachePut can't be applied for types assignable from ${CommonClassNames.mono}", ksFunction)
        } else if (ksFunction.isFlux()) {
            throw ProcessingErrorException("@CachePut can't be applied for types assignable from ${CommonClassNames.flux}", ksFunction)
        } else if (ksFunction.isVoid()) {
            throw ProcessingErrorException("@CachePut can't be applied for types assignable from ${Void::class}", ksFunction)
        }

        val operation = getCacheOperation(ksFunction, aspectContext)
        val body = if (ksFunction.isSuspend()) {
            buildBodySync(ksFunction, operation, superCall)
        } else {
            buildBodySync(ksFunction, operation, superCall)
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
