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
class CacheInvalidateAopKoraAspect(private val resolver: Resolver) : AbstractAopCacheAspect() {

    private val ANNOTATION_CACHE_INVALIDATE = ClassName("io.koraframework.cache.annotation", "CacheInvalidate")
    private val ANNOTATION_CACHE_INVALIDATES = ClassName("io.koraframework.cache.annotation", "CacheInvalidates")

    override fun getSupportedAnnotationTypes(): Set<String> {
        return setOf(ANNOTATION_CACHE_INVALIDATE.canonicalName, ANNOTATION_CACHE_INVALIDATES.canonicalName)
    }

    override fun apply(ksFunction: KSFunctionDeclaration, superCall: String, aspectContext: KoraAspect.AspectContext): KoraAspect.ApplyResult {
        if (ksFunction.isFuture()) {
            throw ProcessingErrorException("@CacheInvalidate can't be applied for types assignable from ${Future::class.java}", ksFunction)
        } else if (ksFunction.isCompletionStage()) {
            throw ProcessingErrorException("@CacheInvalidate can't be applied for types assignable from ${CompletionStage::class.java}", ksFunction)
        } else if (ksFunction.isMono()) {
            throw ProcessingErrorException("@CacheInvalidate can't be applied for types assignable from ${CommonClassNames.mono}", ksFunction)
        } else if (ksFunction.isFlux()) {
            throw ProcessingErrorException("@CacheInvalidate can't be applied for types assignable from ${CommonClassNames.flux}", ksFunction)
        }

        val operation = CacheOperationUtils.Companion.getCacheOperation(ksFunction, aspectContext)
        val body = if (operation.type == CacheOperation.Type.EVICT_ALL) {
            buildBodySyncAll(ksFunction, operation, superCall)
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
