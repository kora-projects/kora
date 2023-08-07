package ru.tinkoff.kora.cache.symbol.processor.aop

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.squareup.kotlinpoet.ClassName
import ru.tinkoff.kora.aop.symbol.processor.KoraAspect
import ru.tinkoff.kora.cache.symbol.processor.CacheOperation

@KspExperimental
abstract class AbstractAopCacheAspect : KoraAspect {

    private val KEY_CACHE = ClassName("ru.tinkoff.kora.cache", "CacheKey")

    open fun getCacheKey(operation: CacheOperation): ClassName {
        return KEY_CACHE
    }

    open fun getCacheFields(
        operation: CacheOperation,
        resolver: Resolver,
        aspectContext: KoraAspect.AspectContext
    ): List<String> {
        val cacheFields: MutableList<String> = ArrayList()
        for (cacheImpl in operation.cacheImplementations) {
            val cacheElement = resolver.getClassDeclarationByName(cacheImpl)
            val fieldCache: String = aspectContext.fieldFactory.constructorParam(cacheElement!!.asType(listOf()), listOf())
            cacheFields.add(fieldCache)
        }
        return cacheFields
    }

    open fun getKeyRecordParameters(operation: CacheOperation, method: KSFunctionDeclaration): String {
        return operation.getParametersNames(method).joinToString(", ")
    }

    open fun getSuperMethod(method: KSFunctionDeclaration, superCall: String): String {
        return method.parameters.joinToString(", ", "$superCall(", ")")
    }
}
