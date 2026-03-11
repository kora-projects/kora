package io.koraframework.cache.symbol.processor.aop

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import io.koraframework.aop.symbol.processor.KoraAspect
import io.koraframework.cache.symbol.processor.CacheOperation
import io.koraframework.cache.symbol.processor.CacheOperationUtils
import io.koraframework.ksp.common.CommonClassNames
import io.koraframework.ksp.common.FunctionUtils.isCompletionStage
import io.koraframework.ksp.common.FunctionUtils.isFlux
import io.koraframework.ksp.common.FunctionUtils.isFuture
import io.koraframework.ksp.common.FunctionUtils.isMono
import io.koraframework.ksp.common.FunctionUtils.isVoid
import io.koraframework.ksp.common.exception.ProcessingErrorException
import java.util.concurrent.CompletionStage
import java.util.concurrent.Future

@KspExperimental
class CachePutAopKoraAspect(private val resolver: Resolver) : AbstractAopCacheAspect() {

    private val ANNOTATION_CACHE_PUT = ClassName("io.koraframework.cache.annotation", "CachePut")
    private val ANNOTATION_CACHE_PUTS = ClassName("io.koraframework.cache.annotation", "CachePuts")

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

        val operation = CacheOperationUtils.Companion.getCacheOperation(ksFunction, aspectContext)
        val body = buildBodySync(ksFunction, operation, superCall)
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
