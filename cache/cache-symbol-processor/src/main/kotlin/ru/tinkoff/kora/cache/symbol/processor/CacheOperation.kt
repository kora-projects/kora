package ru.tinkoff.kora.cache.symbol.processor

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeArgument
import com.google.devtools.ksp.symbol.KSTypeReference
import com.squareup.kotlinpoet.CodeBlock

@KspExperimental
data class CacheOperation(
    val type: Type,
    val executions: List<CacheExecution>,
    val origin: Origin
) {

    data class CacheExecution(
        val field: String,
        val type: KSType,
        val superType: KSTypeReference,
        val cacheKey: CacheKey?
    )

    data class CacheKey(val type: KSTypeArgument, val code: CodeBlock)

    data class Origin(val className: String, val methodName: String) {
        override fun toString(): String = "[class=$className, method=$methodName]"
    }

    enum class Type {
        GET,
        PUT,
        EVICT,
        EVICT_ALL
    }
}
