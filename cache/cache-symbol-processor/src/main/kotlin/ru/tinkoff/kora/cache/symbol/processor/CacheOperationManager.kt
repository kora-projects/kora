package ru.tinkoff.kora.cache.symbol.processor

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.squareup.kotlinpoet.ksp.toClassName
import ru.tinkoff.kora.cache.annotation.*
import ru.tinkoff.kora.cache.symbol.processor.MethodUtils.Companion.getParameters
import ru.tinkoff.kora.cache.symbol.processor.MethodUtils.Companion.getReturnValueCanonicalName
import ru.tinkoff.kora.ksp.common.FunctionUtils.isVoid
import ru.tinkoff.kora.ksp.common.exception.ProcessingError
import ru.tinkoff.kora.ksp.common.exception.ProcessingErrorException
import java.util.regex.Pattern
import javax.tools.Diagnostic

@KspExperimental
class CacheOperationManager {

    private data class CacheSignature(val key: Type, val value: String?, val parameterTypes: List<String>, val origin: CacheMeta.Origin) {

        data class Type(val packageName: String, val simpleName: String)
    }

    companion object {

        private val ANNOTATIONS = setOf(
            Cacheable::class.java, Cacheables::class.java,
            CachePut::class.java, CachePuts::class.java,
            CacheInvalidate::class.java, CacheInvalidates::class.java
        )

        private val NAME_PATTERN = Pattern.compile("^[a-zA-Z][0-9a-zA-Z_]*")
        private val CACHE_NAME_TO_CACHE_KEY: HashMap<String, CacheSignature> = HashMap()

        fun reset() {
            CACHE_NAME_TO_CACHE_KEY.clear()
        }

        fun getCacheMeta(method: KSFunctionDeclaration): CacheMeta {
            val className = method.parentDeclaration?.simpleName?.asString() ?: ""
            val methodName = method.qualifiedName.toString()
            val origin = CacheMeta.Origin(className, methodName)

            val cacheables = getCacheableAnnotations(method)
            val puts = getCachePutAnnotations(method)
            val invalidates = getCacheInvalidateAnnotations(method)

            val annotations = mutableSetOf<String>()
            cacheables.asSequence().forEach { a -> annotations.add(a.javaClass.canonicalName) }
            puts.asSequence().forEach { a -> annotations.add(a.javaClass.canonicalName) }
            invalidates.asSequence().forEach { a -> annotations.add(a.javaClass.canonicalName) }

            if (annotations.size > 1) {
                throw ProcessingErrorException(
                    ProcessingError(
                        "Expected only one type of Cache annotations but was $annotations for $origin",
                        method,
                        Diagnostic.Kind.ERROR
                    )
                )
            }

            if (cacheables.isNotEmpty()) {
                val managers = mutableListOf<CacheMeta.Manager>()
                val managerParameters = mutableListOf<List<String>>()
                for (i in cacheables.indices) {
                    val annotation = cacheables[i]
                    val tags = listOf<String>()
                    val manager = CacheMeta.Manager(annotation.name, tags)
                    managers.add(manager)

                    val annotationParameters = annotation.parameters.toList()
                    for (managerParameter in managerParameters) {
                        if (managerParameter != annotationParameters) {
                            throw ProcessingErrorException(
                                ProcessingError(
                                    "${annotation.javaClass} parameters mismatch for different annotations for $origin",
                                    method,
                                    Diagnostic.Kind.ERROR
                                )
                            )
                        }
                    }
                    managerParameters.add(annotationParameters)
                }

                return CacheMeta(CacheMeta.Type.GET, managers, managerParameters[0], origin)
            } else if (puts.isNotEmpty()) {
                val managers = mutableListOf<CacheMeta.Manager>()
                val managerParameters = mutableListOf<List<String>>()
                for (i in puts.indices) {
                    val annotation = puts[i]
                    val tags = listOf<String>()
                    val manager = CacheMeta.Manager(annotation.name, tags)
                    managers.add(manager)

                    val annotationParameters = annotation.parameters.toList()
                    for (managerParameter in managerParameters) {
                        if (managerParameter != annotationParameters) {
                            throw ProcessingErrorException(
                                ProcessingError(
                                    "${annotation.javaClass} parameters mismatch for different annotations for $origin",
                                    method,
                                    Diagnostic.Kind.ERROR
                                )
                            )
                        }
                    }
                    managerParameters.add(annotationParameters)
                }

                return CacheMeta(CacheMeta.Type.PUT, managers, managerParameters[0], origin)
            } else if (invalidates.isNotEmpty()) {
                val anyInvalidateAll = invalidates.any { a -> a.invalidateAll }
                val allInvalidateAll = invalidates.all { a -> a.invalidateAll }

                if (anyInvalidateAll && !allInvalidateAll) {
                    throw ProcessingErrorException(
                        ProcessingError(
                            "${CacheInvalidate::class.java} not all annotations are marked 'invalidateAll' out of all for $origin",
                            method,
                            Diagnostic.Kind.ERROR
                        )
                    )
                }

                val managers = mutableListOf<CacheMeta.Manager>()
                val managerParameters = mutableListOf<List<String>>()
                for (i in invalidates.indices) {
                    val annotation = invalidates[i]
                    val tags = listOf<String>()
                    val manager = CacheMeta.Manager(annotation.name, tags)
                    managers.add(manager)

                    val annotationParameters = annotation.parameters.toList()
                    for (managerParameter in managerParameters) {
                        if (managerParameter != annotationParameters) {
                            throw ProcessingErrorException(
                                ProcessingError(
                                    "${annotation.javaClass} parameters mismatch for different annotations for $origin",
                                    method,
                                    Diagnostic.Kind.ERROR
                                )
                            )
                        }
                    }
                    managerParameters.add(annotationParameters)
                }

                val type = if (allInvalidateAll) CacheMeta.Type.EVICT_ALL else CacheMeta.Type.EVICT
                return CacheMeta(type, managers, managerParameters[0], origin)
            }
            throw IllegalStateException("None of $ANNOTATIONS cache annotations found")
        }

        fun getCacheOperation(method: KSFunctionDeclaration, env: Resolver): CacheOperation {
            val meta = getCacheMeta(method)
            val signature = getCacheSignature(meta, method, env)
            return CacheOperation(
                meta,
                CacheOperation.Key(signature.key.packageName, signature.key.simpleName),
                CacheOperation.Value(signature.value)
            )
        }

        private fun getCacheSignature(meta: CacheMeta, method: KSFunctionDeclaration, resolver: Resolver): CacheSignature {
            for (manager in meta.managers) {
                if (!NAME_PATTERN.matcher(manager.name).matches()) {
                    throw IllegalArgumentException("Cache name for ${meta.origin} doesn't match pattern: $NAME_PATTERN")
                }
            }

            val parameterTypes = method.getParameters(meta.parameters)
                .map { p -> p.type.resolve().toClassName().canonicalName }
                .toList()

            val returnType = method.returnType
            var cacheSignature = meta.managers.asSequence()
                .map { manager -> CACHE_NAME_TO_CACHE_KEY[manager.name] }
                .filterNotNull()
                .firstOrNull()

            if (cacheSignature == null) {
                val sigCacheName = meta.managers[0].name
                val nonVoidReturnType = if (method.isVoid()) {
                    null
                } else {
                    method.getReturnValueCanonicalName()
                }

                val key = CacheSignature.Type(method.packageName.asString(), "_CacheKey__$sigCacheName")
                val signature = CacheSignature(key, nonVoidReturnType, parameterTypes, meta.origin)
                for (manager in meta.managers) {
                    CACHE_NAME_TO_CACHE_KEY[manager.name] = signature
                }
                cacheSignature = signature
            }

            if (meta.type != CacheMeta.Type.EVICT_ALL && meta.type != CacheMeta.Type.EVICT) {
                if (cacheSignature.parameterTypes != parameterTypes) {
                    throw IllegalStateException("Cache Key parameters from ${cacheSignature.origin} mismatch with ${meta.origin}, expected ${cacheSignature.parameterTypes} but was $parameterTypes")
                }

                // Replace evict (void) operations previously saved
                if (!method.isVoid()) {
                    val returnAsStr = method.getReturnValueCanonicalName()
                    if (cacheSignature.value != null && returnAsStr != cacheSignature.value) {
                        throw IllegalStateException("Cache Value type from ${cacheSignature.origin} mismatch with ${meta.origin}, expected ${cacheSignature.value} but was $returnType")
                    } else if (cacheSignature.value == null) {
                        val nonEvictSignature = CacheSignature(cacheSignature.key, returnAsStr, cacheSignature.parameterTypes, meta.origin)
                        for (manager in meta.managers) {
                            CACHE_NAME_TO_CACHE_KEY[manager.name] = nonEvictSignature
                        }
                    }
                }
            }

            return cacheSignature
        }

        private fun getCacheableAnnotations(method: KSFunctionDeclaration): List<Cacheable> {
            val annotationAggregate = method.getAnnotationsByType(Cacheables::class).firstOrNull()
            if (annotationAggregate != null) {
                return annotationAggregate.value.toList()
            }

            return method.getAnnotationsByType(Cacheable::class).toList();
        }

        private fun getCachePutAnnotations(method: KSFunctionDeclaration): List<CachePut> {
            val annotationAggregate = method.getAnnotationsByType(CachePuts::class).firstOrNull()
            if (annotationAggregate != null) {
                return annotationAggregate.value.toList()
            }

            return method.getAnnotationsByType(CachePut::class).toList()
        }

        private fun getCacheInvalidateAnnotations(method: KSFunctionDeclaration): List<CacheInvalidate> {
            val annotationAggregate = method.getAnnotationsByType(CacheInvalidates::class).firstOrNull()
            if (annotationAggregate != null) {
                return annotationAggregate.value.toList()
            }

            return method.getAnnotationsByType(CacheInvalidate::class).toList()
        }
    }
}
