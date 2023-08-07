package ru.tinkoff.kora.cache.symbol.processor

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSValueParameter
import ru.tinkoff.kora.cache.symbol.processor.MethodUtils.Companion.getParameters

@KspExperimental
data class CacheOperation(
    val type: Type,
    val cacheImplementations: List<String>,
    val parameters: List<KSValueParameter>,
    val origin: Origin
) {

    enum class Type {
        GET, PUT, EVICT, EVICT_ALL
    }

    data class Origin(val className: String, val methodName: String) {
        override fun toString(): String = "[class=$className, method=$methodName]"
    }

    fun getParametersNames(method: KSFunctionDeclaration): List<String> {
        return method.getParameters(parameters.asSequence().map { p -> p.name!!.getShortName() }.toList()).asSequence()
            .map { it.name!!.getShortName() }
            .toList()
    }
}
