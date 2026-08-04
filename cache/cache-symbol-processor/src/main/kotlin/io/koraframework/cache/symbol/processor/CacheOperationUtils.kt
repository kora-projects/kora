package io.koraframework.cache.symbol.processor

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getConstructors
import com.google.devtools.ksp.getAllSuperTypes
import com.google.devtools.ksp.isConstructor
import com.google.devtools.ksp.isPublic
import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.ksp.toTypeName
import io.koraframework.aop.symbol.processor.KoraAspect
import io.koraframework.ksp.common.AnnotationUtils.findAnnotations
import io.koraframework.ksp.common.KoraSymbolProcessingEnv
import io.koraframework.ksp.common.FunctionUtils.isCompletionStage
import io.koraframework.ksp.common.FunctionUtils.isFlow
import io.koraframework.ksp.common.FunctionUtils.isFlux
import io.koraframework.ksp.common.FunctionUtils.isFuture
import io.koraframework.ksp.common.FunctionUtils.isMono
import io.koraframework.ksp.common.FunctionUtils.isPublisher
import io.koraframework.ksp.common.FunctionUtils.isVoid
import io.koraframework.ksp.common.KspCommonUtils.findRepeatableAnnotation
import io.koraframework.ksp.common.MappersData
import io.koraframework.ksp.common.MappingData
import io.koraframework.ksp.common.TagUtils.toTagAnnotation
import io.koraframework.ksp.common.exception.ProcessingError
import io.koraframework.ksp.common.exception.ProcessingErrorException
import io.koraframework.ksp.common.parseMappingData
import java.util.stream.Stream
import javax.tools.Diagnostic

@KspExperimental
class CacheOperationUtils {

    companion object {

        private val KEY_MAPPER_1 = ClassName("io.koraframework.cache", "CacheKeyMapper")
        private val KEY_MAPPER_2 = ClassName("io.koraframework.cache", "CacheKeyMapper", "CacheKeyMapper2")
        private val KEY_MAPPER_3 = ClassName("io.koraframework.cache", "CacheKeyMapper", "CacheKeyMapper3")
        private val KEY_MAPPER_4 = ClassName("io.koraframework.cache", "CacheKeyMapper", "CacheKeyMapper4")
        private val KEY_MAPPER_5 = ClassName("io.koraframework.cache", "CacheKeyMapper", "CacheKeyMapper5")
        private val KEY_MAPPER_6 = ClassName("io.koraframework.cache", "CacheKeyMapper", "CacheKeyMapper6")
        private val KEY_MAPPER_7 = ClassName("io.koraframework.cache", "CacheKeyMapper", "CacheKeyMapper7")
        private val KEY_MAPPER_8 = ClassName("io.koraframework.cache", "CacheKeyMapper", "CacheKeyMapper8")
        private val KEY_MAPPER_9 = ClassName("io.koraframework.cache", "CacheKeyMapper", "CacheKeyMapper9")

        val REDIS_CACHE = ClassName("io.koraframework.cache.redis", "RedisCache")
        val CAFFEINE_CACHE = ClassName("io.koraframework.cache.caffeine", "CaffeineCache")
        val ANNOTATION_CACHEABLE = ClassName("io.koraframework.cache.annotation", "Cacheable")
        val ANNOTATION_CACHEABLES = ClassName("io.koraframework.cache.annotation", "Cacheables")
        val ANNOTATION_CACHE_PUT = ClassName("io.koraframework.cache.annotation", "CachePut")
        val ANNOTATION_CACHE_PUTS = ClassName("io.koraframework.cache.annotation", "CachePuts")
        val ANNOTATION_CACHE_INVALIDATE = ClassName("io.koraframework.cache.annotation", "CacheInvalidate")
        val ANNOTATION_CACHE_INVALIDATES = ClassName("io.koraframework.cache.annotation", "CacheInvalidates")
        val ANNOTATION_CACHE_INVALIDATE_ALL = ClassName("io.koraframework.cache.annotation", "CacheInvalidateAll")
        val ANNOTATION_CACHE_INVALIDATE_ALLS = ClassName("io.koraframework.cache.annotation", "CacheInvalidateAlls")

        private val ANNOTATIONS = setOf(
            ANNOTATION_CACHEABLE.canonicalName, ANNOTATION_CACHEABLES.canonicalName,
            ANNOTATION_CACHE_PUT.canonicalName, ANNOTATION_CACHE_PUTS.canonicalName,
            ANNOTATION_CACHE_INVALIDATE.canonicalName, ANNOTATION_CACHE_INVALIDATES.canonicalName,
            ANNOTATION_CACHE_INVALIDATE_ALL.canonicalName, ANNOTATION_CACHE_INVALIDATE_ALLS.canonicalName
        )

        fun getCacheOperation(
            method: KSFunctionDeclaration,
            aspectContext: KoraAspect.AspectContext
        ): CacheOperation {
            val className = method.parentDeclaration?.simpleName?.asString() ?: ""
            val methodName = method.qualifiedName.toString()
            val origin = CacheOperation.Origin(className, methodName)

            val cacheables = getCacheableAnnotations(method)
            val puts = getCachePutAnnotations(method)
            val invalidates = getCacheInvalidateAnnotations(method)
            val invalidateAlls = getCacheInvalidateAllAnnotations(method)

            val annotations = mutableSetOf<String>()
            cacheables.asSequence().forEach { a -> annotations.add(a.javaClass.canonicalName) }
            puts.asSequence().forEach { a -> annotations.add(a.javaClass.canonicalName) }
            invalidates.asSequence().forEach { a -> annotations.add(a.javaClass.canonicalName) }
            invalidateAlls.asSequence().forEach { a -> annotations.add(a.javaClass.canonicalName) }

            if (annotations.size > 1) {
                throw ProcessingErrorException(
                    ProcessingError(
                        mixedCacheOperationAnnotationsError(origin, annotations),
                        method,
                        Diagnostic.Kind.ERROR
                    )
                )
            }

            if (cacheables.isNotEmpty()) {
                return getCacheOperation(method, CacheOperation.Type.GET, cacheables, aspectContext)
            } else if (puts.isNotEmpty()) {
                return getCacheOperation(method, CacheOperation.Type.PUT, puts, aspectContext)
            } else if (invalidates.isNotEmpty()) {
                val type = CacheOperation.Type.EVICT
                return getCacheOperation(method, type, invalidates, aspectContext)
            } else if (invalidateAlls.isNotEmpty()) {
                val type = CacheOperation.Type.EVICT_ALL
                return getCacheOperation(method, type, invalidateAlls, aspectContext)
            }

            throw IllegalStateException(noCacheAnnotationInternalError(origin))
        }

        private fun getCacheOperation(
            method: KSFunctionDeclaration,
            type: CacheOperation.Type,
            annotations: List<KSAnnotation>,
            aspectContext: KoraAspect.AspectContext
        ): CacheOperation {
            val className = method.parentDeclaration?.simpleName?.asString() ?: ""
            val methodName = method.qualifiedName.toString()
            val origin = CacheOperation.Origin(className, methodName)

            val cacheExecs = mutableListOf<CacheOperation.CacheExecution>()
            val allParameters = mutableListOf<List<String>>()
            for (i in annotations.indices) {
                val annotation = annotations[i]

                val parameters: List<String> = annotation.arguments.filter { a -> a.name!!.asString() == "args" }
                    .map { it.value as List<*> }
                    .firstOrNull { it.isNotEmpty() }
                    ?.map { it as String }
                    ?: method.parameters.asSequence().map { p -> p.name!!.asString() }.toList()

                for (parameter in allParameters) {
                    if (parameter != parameters) {
                        throw ProcessingErrorException(
                            ProcessingError(
                                cacheAnnotationArgumentsMismatchError(annotation.shortName.asString(), origin, parameter, parameters),
                                method,
                                Diagnostic.Kind.ERROR
                            )
                        )
                    }
                }

                val cacheImpl = annotation.arguments.filter { a -> a.name!!.asString() == "value" }
                    .map { a -> a.value as KSType }
                    .first()
                val async = annotation.arguments.filter { a -> a.name!!.asString() == "mode" }
                    .map { a -> a.value.toString().endsWith(".ASYNC") }
                    .firstOrNull()
                    ?: false

                val fieldCache = aspectContext.fieldFactory.constructorParam(cacheImpl, listOf())
                val superTypes = (cacheImpl.declaration as KSClassDeclaration).superTypes.toList()
                val superType = superTypes[superTypes.size - 1]
                val isCaffeine = isCaffeineCache(cacheImpl)
                if (async && isCaffeine) {
                    KoraSymbolProcessingEnv.logger.warn(
                        "Cache async mode is ignored for CaffeineCache ${(cacheImpl.declaration as KSClassDeclaration).qualifiedName!!.asString()}",
                        method
                    )
                }

                var cacheKey: CacheOperation.CacheKey?
                val cacheKeyMirror = superType.resolve().arguments[0]
                val cacheKeyDeclaration = cacheKeyMirror.type!!.resolve().declaration as KSClassDeclaration

                val mapper = getSuitableMapper(method.parseMappingData())
                if (mapper?.mapper != null) {
                    val tags = mapper.tag?.toTagAnnotation()
                        ?.let { listOf(it) }
                        ?: listOf()
                    val fieldMapper = aspectContext.fieldFactory.constructorParam(mapper.mapper!!, tags)
                    cacheKey = CacheOperation.CacheKey(
                        cacheKeyMirror,
                        CodeBlock.of("%L.map(%L)", fieldMapper, parameters.joinToString(", "))
                    )
                } else if (parameters.size == 1) {
                    cacheKey = CacheOperation.CacheKey(cacheKeyMirror, CodeBlock.of(parameters[0]))
                } else if (type == CacheOperation.Type.EVICT_ALL) {
                    cacheKey = null
                } else {
                    val parameterResult = parameters.asSequence()
                        .flatMap { param ->
                            method.parameters.asSequence().filter { p -> p.name!!.asString() == param }
                        }
                        .toList()

                    val keyConstructor = findKeyConstructor(cacheKeyDeclaration, parameterResult)
                    if (keyConstructor != null) {
                        cacheKey = CacheOperation.CacheKey(
                            cacheKeyMirror,
                            CodeBlock.of("%T(%L)", cacheKeyMirror.toTypeName(), parameters.joinToString(", "))
                        )
                    } else {
                        if (parameters.size > 9) {
                            throw ProcessingErrorException(
                                tooManyCacheKeyArgumentsError(annotation.shortName.asString(), origin, parameters),
                                method
                            )
                        }

                        if (parameters.isEmpty() && (type == CacheOperation.Type.GET || type == CacheOperation.Type.EVICT)) {
                            throw ProcessingErrorException(
                                emptyCacheKeyArgumentsError(annotation.shortName.asString(), origin),
                                method
                            )
                        }

                        val mapperType = getKeyMapper(cacheKeyMirror, parameterResult)
                        val fieldMapper = aspectContext.fieldFactory.constructorParam(mapperType, listOf())
                        cacheKey = CacheOperation.CacheKey(
                            cacheKeyMirror,
                            CodeBlock.of("%L.map(%L)", fieldMapper, parameters.joinToString(", "))
                        )
                    }
                }

                allParameters.add(parameters)
                cacheExecs.add(CacheOperation.CacheExecution(fieldCache, cacheImpl, superType, cacheKey, async, isCaffeine))
            }

            return CacheOperation(type, cacheExecs, origin)
        }

        private fun isCaffeineCache(type: KSType): Boolean {
            val declaration = type.declaration as? KSClassDeclaration ?: return false
            if (declaration.qualifiedName?.asString() == CAFFEINE_CACHE.canonicalName) {
                return true
            }

            return declaration.getAllSuperTypes().any {
                (it.declaration as? KSClassDeclaration)?.qualifiedName?.asString() == CAFFEINE_CACHE.canonicalName
            }
        }

        private fun getCacheableAnnotations(method: KSFunctionDeclaration): List<KSAnnotation> {
            val annotationAggregate = method.findRepeatableAnnotation(ANNOTATION_CACHEABLE, ANNOTATION_CACHEABLES)
            if (annotationAggregate.isNotEmpty()) {
                return annotationAggregate
            }

            return method.findAnnotations(ANNOTATION_CACHEABLE).toList()
        }

        private fun getCachePutAnnotations(method: KSFunctionDeclaration): List<KSAnnotation> {
            val annotationAggregate = method.findRepeatableAnnotation(ANNOTATION_CACHE_PUT, ANNOTATION_CACHE_PUTS)
            if (annotationAggregate.isNotEmpty()) {
                return annotationAggregate
            }

            return method.findAnnotations(ANNOTATION_CACHE_PUT).toList()
        }

        private fun getCacheInvalidateAnnotations(method: KSFunctionDeclaration): List<KSAnnotation> {
            val annotationAggregate = method.findRepeatableAnnotation(ANNOTATION_CACHE_INVALIDATE, ANNOTATION_CACHE_INVALIDATES)
            if (annotationAggregate.isNotEmpty()) {
                return annotationAggregate
            }

            return method.findAnnotations(ANNOTATION_CACHE_INVALIDATE).toList()
        }

        private fun getCacheInvalidateAllAnnotations(method: KSFunctionDeclaration): List<KSAnnotation> {
            val annotationAggregate = method.findRepeatableAnnotation(ANNOTATION_CACHE_INVALIDATE_ALL, ANNOTATION_CACHE_INVALIDATE_ALLS)
            if (annotationAggregate.isNotEmpty()) {
                return annotationAggregate
            }

            return method.findAnnotations(ANNOTATION_CACHE_INVALIDATE_ALL).toList()
        }

        private fun getSuitableMapper(mappers: MappersData): MappingData? {
            return if (mappers.mapperClasses.isEmpty()) {
                null
            } else Stream.of<MappingData>(
                mappers.getMapping(KEY_MAPPER_1),
                mappers.getMapping(KEY_MAPPER_2),
                mappers.getMapping(KEY_MAPPER_3),
                mappers.getMapping(KEY_MAPPER_4),
                mappers.getMapping(KEY_MAPPER_5),
                mappers.getMapping(KEY_MAPPER_6),
                mappers.getMapping(KEY_MAPPER_7),
                mappers.getMapping(KEY_MAPPER_8),
                mappers.getMapping(KEY_MAPPER_9)
            )
                .filter { it != null }
                .filter { m -> m.mapper != null }
                .findFirst()
                .orElse(null)
        }

        private fun getKeyMapper(
            cacheKeyMirror: KSTypeArgument,
            parameters: List<KSValueParameter>
        ): ParameterizedTypeName {
            val mapper = when (parameters.size) {
                1 -> KEY_MAPPER_1
                2 -> KEY_MAPPER_2
                3 -> KEY_MAPPER_3
                4 -> KEY_MAPPER_4
                5 -> KEY_MAPPER_5
                6 -> KEY_MAPPER_6
                7 -> KEY_MAPPER_7
                8 -> KEY_MAPPER_8
                9 -> KEY_MAPPER_9
                else -> throw ProcessingErrorException(
                    ProcessingError(
                        unsupportedCacheKeyMapperArgumentsError(parameters.size),
                        parameters.firstOrNull()
                    )
                )
            }

            val args = ArrayList<TypeName>()
            args.add(cacheKeyMirror.toTypeName())
            parameters.forEach { a -> args.add(a.type.toTypeName()) }

            return mapper.parameterizedBy(args)
        }

        private fun findKeyConstructor(
            type: KSClassDeclaration,
            parameters: List<KSValueParameter>
        ): KSFunctionDeclaration? {
            val constructors = type.getConstructors()
                .filter { e -> e.isConstructor() }
                .filter { c -> c.isPublic() }
                .filter { c -> c.parameters.size == parameters.size }
                .toList()

            if (constructors.isEmpty()) {
                return null
            }

            for (constructor in constructors) {
                val constructorParams = constructor.parameters
                var isCandidate = true
                for (i in parameters.indices) {
                    val methodParam = parameters[i]
                    val constructorParam = constructorParams[i]
                    val mType = methodParam.type.resolve()
                    val cType = constructorParam.type.resolve()
                    val isAssignable = mType.makeNullable().isAssignableFrom(cType)
                    if (!isAssignable || (!cType.isMarkedNullable && mType.isMarkedNullable)) {
                        isCandidate = false
                        break
                    }
                }
                if (isCandidate) {
                    return constructor
                }
            }

            return null
        }

        private fun mixedCacheOperationAnnotationsError(origin: CacheOperation.Origin, annotations: Set<String>): String {
            return """
                Invalid cache annotations on `$origin`.

                A method can use only one cache operation type at a time.
                Found annotation types: $annotations

                Fix: keep one of `@Cacheable`, `@CachePut`, `@CacheInvalidate`, or `@CacheInvalidateAll` on this method.
            """.trimIndent()
        }

        private fun noCacheAnnotationInternalError(origin: CacheOperation.Origin): String {
            return """
                Kora internal error: cache operation was requested for `$origin`, but no supported cache annotation was found.

                Supported annotations: $ANNOTATIONS
                Please report this with the annotated method source.
            """.trimIndent()
        }

        private fun cacheAnnotationArgumentsMismatchError(
            annotationName: String,
            origin: CacheOperation.Origin,
            previous: List<String>,
            current: List<String>
        ): String {
            return """
                Invalid repeated `@$annotationName` declarations on `$origin`.

                All repeated cache annotations of the same operation must use the same `args` list.
                First args: $previous
                Current args: $current

                Fix: make every repeated `@$annotationName(args = ...)` use the same argument names, or split the method.
            """.trimIndent()
        }

        private fun tooManyCacheKeyArgumentsError(annotationName: String, origin: CacheOperation.Origin, parameters: List<String>): String {
            return """
                Invalid cache key for `@$annotationName` on `$origin`.

                Cache key generation supports at most 9 method arguments, but found ${parameters.size}.
                Selected args: $parameters

                Fix: provide a custom `CacheKeyMapper`, reduce `args`, or wrap key fields into a single key object.
            """.trimIndent()
        }

        private fun emptyCacheKeyArgumentsError(annotationName: String, origin: CacheOperation.Origin): String {
            return """
                Invalid cache key for `@$annotationName` on `$origin`.

                This cache operation requires at least one key argument, but `args` is empty.

                Fix: add at least one method argument to `args`, remove `args`, or use `@CacheInvalidateAll` when the whole cache should be cleared.
            """.trimIndent()
        }

        private fun unsupportedCacheKeyMapperArgumentsError(argumentCount: Int): String {
            return """
                Invalid cache key mapper arity.

                Built-in `CacheKeyMapper` supports from 1 to 9 method arguments, but found $argumentCount.

                Fix: provide a custom `CacheKeyMapper`, reduce selected `args`, or wrap key fields into a single key object.
            """.trimIndent()
        }
    }
}
