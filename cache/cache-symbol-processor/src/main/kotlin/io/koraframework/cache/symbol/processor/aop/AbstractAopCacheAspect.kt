package io.koraframework.cache.symbol.processor.aop

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import io.koraframework.aop.symbol.processor.KoraAspect
import io.koraframework.ksp.common.TagUtils.toTagAnnotation
import io.koraframework.cache.symbol.processor.CacheOperation

@KspExperimental
abstract class AbstractAopCacheAspect : KoraAspect {

    private val executor = ClassName("java.util.concurrent", "Executor")
    private val cacheMode = ClassName("io.koraframework.cache.annotation", "CacheMode")

    open fun getSuperMethod(method: KSFunctionDeclaration, superCall: String): String {
        return method.parameters.joinToString(", ", "$superCall(", ")")
    }

    fun getExecutorField(operation: CacheOperation, aspectContext: KoraAspect.AspectContext): String? {
        if (operation.executions.none { isAsync(it) }) {
            return null
        }

        return aspectContext.fieldFactory.constructorParam(executor, listOf(cacheMode.toTagAnnotation()))
    }

    fun isAsync(cache: CacheOperation.CacheExecution): Boolean {
        return cache.async && !cache.caffeine
    }

    fun cachePut(executorField: String?, cache: CacheOperation.CacheExecution, key: String, value: String): CodeBlock {
        return if (isAsync(cache)) {
            CodeBlock.of("%L.execute { %L.put(%L, %L) }\n", executorField, cache.field, key, value)
        } else {
            CodeBlock.of("%L.put(%L, %L)\n", cache.field, key, value)
        }
    }

    fun cacheInvalidate(executorField: String?, cache: CacheOperation.CacheExecution, key: String): CodeBlock {
        return if (isAsync(cache)) {
            CodeBlock.of("%L.execute { %L.invalidate(%L) }\n", executorField, cache.field, key)
        } else {
            CodeBlock.of("%L.invalidate(%L)\n", cache.field, key)
        }
    }

    fun cacheInvalidateAll(executorField: String?, cache: CacheOperation.CacheExecution): CodeBlock {
        return if (isAsync(cache)) {
            CodeBlock.of("%L.execute { %L.invalidateAll() }\n", executorField, cache.field)
        } else {
            CodeBlock.of("%L.invalidateAll()\n", cache.field)
        }
    }
}
