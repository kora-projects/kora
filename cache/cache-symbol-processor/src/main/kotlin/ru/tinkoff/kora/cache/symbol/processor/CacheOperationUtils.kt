package ru.tinkoff.kora.cache.symbol.processor

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getConstructors
import com.google.devtools.ksp.isConstructor
import com.google.devtools.ksp.isPublic
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import jakarta.annotation.Nullable
import ru.tinkoff.kora.aop.symbol.processor.KoraAspect
import ru.tinkoff.kora.ksp.common.FunctionUtils.isFlow
import ru.tinkoff.kora.ksp.common.FunctionUtils.isFlux
import ru.tinkoff.kora.ksp.common.FunctionUtils.isCompletionStage
import ru.tinkoff.kora.ksp.common.FunctionUtils.isFuture
import ru.tinkoff.kora.ksp.common.FunctionUtils.isMono
import ru.tinkoff.kora.ksp.common.FunctionUtils.isPublisher
import ru.tinkoff.kora.ksp.common.FunctionUtils.isVoid
import ru.tinkoff.kora.ksp.common.MappersData
import ru.tinkoff.kora.ksp.common.MappingData
import ru.tinkoff.kora.ksp.common.TagUtils.toTagAnnotation
import ru.tinkoff.kora.ksp.common.exception.ProcessingError
import ru.tinkoff.kora.ksp.common.exception.ProcessingErrorException
import ru.tinkoff.kora.ksp.common.parseMappingData
import java.util.stream.Stream
import javax.tools.Diagnostic

@KspExperimental
class CacheOperationUtils {

    companion object {

        private val KEY_MAPPER_1 = ClassName("ru.tinkoff.kora.cache", "CacheKeyMapper")
        private val KEY_MAPPER_2 = ClassName("ru.tinkoff.kora.cache", "CacheKeyMapper", "CacheKeyMapper2")
        private val KEY_MAPPER_3 = ClassName("ru.tinkoff.kora.cache", "CacheKeyMapper", "CacheKeyMapper3")
        private val KEY_MAPPER_4 = ClassName("ru.tinkoff.kora.cache", "CacheKeyMapper", "CacheKeyMapper4")
        private val KEY_MAPPER_5 = ClassName("ru.tinkoff.kora.cache", "CacheKeyMapper", "CacheKeyMapper5")
        private val KEY_MAPPER_6 = ClassName("ru.tinkoff.kora.cache", "CacheKeyMapper", "CacheKeyMapper6")
        private val KEY_MAPPER_7 = ClassName("ru.tinkoff.kora.cache", "CacheKeyMapper", "CacheKeyMapper7")
        private val KEY_MAPPER_8 = ClassName("ru.tinkoff.kora.cache", "CacheKeyMapper", "CacheKeyMapper8")
        private val KEY_MAPPER_9 = ClassName("ru.tinkoff.kora.cache", "CacheKeyMapper", "CacheKeyMapper9")

        private val REDIS_CACHE = ClassName("ru.tinkoff.kora.cache.redis", "RedisCache")
        private val CACHE_ASYNC = ClassName("ru.tinkoff.kora.cache", "AsyncCache")
        private val ANNOTATION_CACHEABLE = ClassName("ru.tinkoff.kora.cache.annotation", "Cacheable")
        private val ANNOTATION_CACHEABLES = ClassName("ru.tinkoff.kora.cache.annotation", "Cacheables")
        private val ANNOTATION_CACHE_PUT = ClassName("ru.tinkoff.kora.cache.annotation", "CachePut")
        private val ANNOTATION_CACHE_PUTS = ClassName("ru.tinkoff.kora.cache.annotation", "CachePuts")
        private val ANNOTATION_CACHE_INVALIDATE = ClassName("ru.tinkoff.kora.cache.annotation", "CacheInvalidate")
        private val ANNOTATION_CACHE_INVALIDATES = ClassName("ru.tinkoff.kora.cache.annotation", "CacheInvalidates")

        private val ANNOTATIONS = setOf(
            ANNOTATION_CACHEABLE.canonicalName, ANNOTATION_CACHEABLES.canonicalName,
            ANNOTATION_CACHE_PUT.canonicalName, ANNOTATION_CACHE_PUTS.canonicalName,
            ANNOTATION_CACHE_INVALIDATE.canonicalName, ANNOTATION_CACHE_INVALIDATES.canonicalName
        )

        fun getCacheOperation(
            method: KSFunctionDeclaration,
            resolver: Resolver,
            aspectContext: KoraAspect.AspectContext
        ): CacheOperation {
            val className = method.parentDeclaration?.simpleName?.asString() ?: ""
            val methodName = method.qualifiedName.toString()
            val origin = CacheOperation.Origin(className, methodName)

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
                return getCacheOperation(method, CacheOperation.Type.GET, cacheables, resolver, aspectContext)
            } else if (puts.isNotEmpty()) {
                return getCacheOperation(method, CacheOperation.Type.PUT, puts, resolver, aspectContext)
            } else if (invalidates.isNotEmpty()) {
                val invalidateAlls = invalidates.asSequence()
                    .flatMap { a -> a.arguments.asSequence() }
                    .filter { a -> a.name!!.getShortName() == "invalidateAll" }
                    .map { a -> a.value as Boolean }
                    .toList()

                val anyInvalidateAll = invalidateAlls.any { v -> v }
                val allInvalidateAll = invalidateAlls.all { v -> v }

                if (anyInvalidateAll && !allInvalidateAll) {
                    throw ProcessingErrorException(
                        ProcessingError(
                            "${ANNOTATION_CACHE_INVALIDATE.canonicalName} not all annotations are marked 'invalidateAll' out of all for " + origin,
                            method,
                            Diagnostic.Kind.ERROR,
                        )
                    )
                }

                val type = if (allInvalidateAll) CacheOperation.Type.EVICT_ALL else CacheOperation.Type.EVICT
                return getCacheOperation(method, type, invalidates, resolver, aspectContext)
            }

            throw IllegalStateException("None of $ANNOTATIONS cache annotations found")
        }

        private fun getCacheOperation(
            method: KSFunctionDeclaration,
            type: CacheOperation.Type,
            annotations: List<KSAnnotation>,
            resolver: Resolver,
            aspectContext: KoraAspect.AspectContext
        ): CacheOperation {
            val className = method.parentDeclaration?.simpleName?.asString() ?: ""
            val methodName = method.qualifiedName.toString()
            val origin = CacheOperation.Origin(className, methodName)

            if (type == CacheOperation.Type.GET || type == CacheOperation.Type.PUT) {
                if (method.isVoid()) {
                    throw IllegalArgumentException("@${annotations[0].shortName.getShortName()} annotation can't return Void type, but was for $origin")
                }
            }

            if (method.isMono() || method.isFlux() || method.isPublisher() || method.isFuture() || method.isCompletionStage() || method.isFlow()) {
                throw IllegalArgumentException("@${annotations[0].shortName.getShortName()} annotation doesn't support return type ${method.returnType} in $origin")
            }

            val cacheExecs = mutableListOf<CacheOperation.CacheExecution>()
            val allParameters = mutableListOf<List<String>>()
            for (i in annotations.indices) {
                val annotation = annotations[i]

                val parameters: List<String> = annotation.arguments.filter { a -> a.name!!.asString() == "parameters" }
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

                var cacheKey: CacheOperation.CacheKey? = null
                val cacheKeyMirror = superType.resolve().arguments[0]
                val cacheKeyDeclaration = cacheKeyMirror.type!!.resolve().declaration as KSClassDeclaration

                val mapper = getSuitableMapper(method.parseMappingData())
                if (mapper?.mapper != null) {
                    val tags = if (mapper.tags.isEmpty())
                        listOf()
                    else
                        listOf(mapper.tags.toTagAnnotation())

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
                                "@${annotations.first().shortName.asString()} doesn't support more than 9 parameters for Cache Key",
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

                var contractType = CacheOperation.CacheExecution.Contract.SYNC
                if (superTypes.any { t -> t.resolve().toClassName() == CACHE_ASYNC }) {
                    contractType = CacheOperation.CacheExecution.Contract.ASYNC
                }

                allParameters.add(parameters)
                cacheExecs.add(CacheOperation.CacheExecution(fieldCache, cacheImpl, superType, contractType, cacheKey))
            }

            return CacheOperation(type, cacheExecs, origin)
        }

        private fun getCacheableAnnotations(method: KSFunctionDeclaration): List<KSAnnotation> {
            method.annotations
                .filter { a -> a.annotationType.resolve().toClassName() == ANNOTATION_CACHEABLES }
                .map { a -> a.arguments[0] }
                .map { arg -> arg.value }
                .toList()

            val annotationAggregate = method.annotations
                .filter { a -> a.annotationType.resolve().toClassName() == ANNOTATION_CACHEABLES }
                .firstOrNull()
            if (annotationAggregate != null) {
                return emptyList()
            }

            return method.annotations
                .filter { a -> a.annotationType.resolve().toClassName() == ANNOTATION_CACHEABLE }
                .toList()
        }

        private fun getCachePutAnnotations(method: KSFunctionDeclaration): List<KSAnnotation> {
            val annotationAggregate = method.annotations
                .filter { a -> a.annotationType.resolve().toClassName() == ANNOTATION_CACHE_PUTS }
                .firstOrNull()

            if (annotationAggregate != null) {
                return emptyList()
            }

            return method.annotations
                .filter { a -> a.annotationType.resolve().toClassName() == ANNOTATION_CACHE_PUT }
                .toList()
        }

        private fun getCacheInvalidateAnnotations(method: KSFunctionDeclaration): List<KSAnnotation> {
            val annotationAggregate = method.annotations
                .filter { a -> a.annotationType.resolve().toClassName() == ANNOTATION_CACHE_INVALIDATES }
                .firstOrNull()

            if (annotationAggregate != null) {
                return emptyList()
            }

            return method.annotations
                .filter { a -> a.annotationType.resolve().toClassName() == ANNOTATION_CACHE_INVALIDATE }
                .toList()
        }

        @Nullable
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
