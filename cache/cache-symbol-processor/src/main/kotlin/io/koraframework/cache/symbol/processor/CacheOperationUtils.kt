package io.koraframework.cache.symbol.processor

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getConstructors
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
                        "Expected only one type of Cache annotations but was $annotations for $origin",
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

            throw IllegalStateException("None of $ANNOTATIONS cache annotations found")
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
                                "${annotation.javaClass} parameters mismatch for different annotations for $origin",
                                method,
                                Diagnostic.Kind.ERROR
                            )
                        )
                    }
                }

                val cacheImpl = annotation.arguments.filter { a -> a.name!!.asString() == "value" }
                    .map { a -> a.value as KSType }
                    .first()

                val fieldCache = aspectContext.fieldFactory.constructorParam(cacheImpl, listOf())
                val superTypes = (cacheImpl.declaration as KSClassDeclaration).superTypes.toList()
                val superType = superTypes[superTypes.size - 1]

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
                                "@${annotation.shortName.asString()} doesn't support more than 9 method arguments for Cache Key",
                                method
                            )
                        }

                        if (parameters.isEmpty() && (type == CacheOperation.Type.GET || type == CacheOperation.Type.EVICT)) {
                            throw ProcessingErrorException(
                                "@${annotation.shortName.asString()} requires minimum 1 Cache Key method argument, but got 0",
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
                cacheExecs.add(CacheOperation.CacheExecution(fieldCache, cacheImpl, superType, cacheKey))
            }

            return CacheOperation(type, cacheExecs, origin)
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
                        "Cache doesn't support ${parameters.size} parameters for Cache Key",
                        parameters[0]
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
    }
}
