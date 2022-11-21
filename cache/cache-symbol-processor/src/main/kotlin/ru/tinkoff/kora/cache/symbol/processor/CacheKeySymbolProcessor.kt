package ru.tinkoff.kora.cache.symbol.processor

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.writeTo
import ru.tinkoff.kora.cache.CacheKey
import ru.tinkoff.kora.cache.annotation.*
import ru.tinkoff.kora.cache.symbol.processor.MethodUtils.Companion.getParameters
import ru.tinkoff.kora.ksp.common.BaseSymbolProcessor
import ru.tinkoff.kora.ksp.common.FunctionUtils.isFlow
import ru.tinkoff.kora.ksp.common.FunctionUtils.isFlux
import ru.tinkoff.kora.ksp.common.FunctionUtils.isFuture
import ru.tinkoff.kora.ksp.common.FunctionUtils.isMono
import ru.tinkoff.kora.ksp.common.FunctionUtils.isPublisher
import ru.tinkoff.kora.ksp.common.FunctionUtils.isVoid
import ru.tinkoff.kora.ksp.common.exception.ProcessingError
import ru.tinkoff.kora.ksp.common.exception.ProcessingErrorException
import ru.tinkoff.kora.ksp.common.visitFunction
import java.io.IOException
import javax.annotation.processing.Generated

@KspExperimental
class CacheKeySymbolProcessor(
    private val environment: SymbolProcessorEnvironment,
    private val generatedCacheKeys: HashSet<String>
) : BaseSymbolProcessor(environment) {

    private val cacheAnnotations = setOf(
        Cacheable::class, Cacheables::class,
        CachePut::class, CachePuts::class,
        CacheInvalidate::class, CacheInvalidates::class
    )

    override fun processRound(resolver: Resolver): List<KSAnnotated> {
        val symbols = resolver.getSymbolsWithAnnotation(Cacheable::class.qualifiedName!!)
            .plus(resolver.getSymbolsWithAnnotation(Cacheables::class.qualifiedName!!))
            .plus(resolver.getSymbolsWithAnnotation(CachePut::class.qualifiedName!!))
            .plus(resolver.getSymbolsWithAnnotation(CachePuts::class.qualifiedName!!))
            .plus(resolver.getSymbolsWithAnnotation(CacheInvalidate::class.qualifiedName!!))
            .plus(resolver.getSymbolsWithAnnotation(CacheInvalidates::class.qualifiedName!!))
            .toList()

        val symbolsToProcess = symbols.filter { it.validate() }
        symbolsToProcess.forEach {
            it.visitFunction { method ->
                val cacheAnnotations = method.annotations
                    .filter { a ->
                        val canonicalName = a.annotationType.resolve().toClassName().canonicalName
                        cacheAnnotations.any { an -> an.qualifiedName == canonicalName }
                    }.toList()

                if (cacheAnnotations.isNotEmpty()) {
                    try {
                        val annotationNames = cacheAnnotations.map { a -> a.shortName.getShortName() }.toList()
                        val operation = CacheOperationManager.getCacheOperation(method, resolver)

                        if (operation.meta.type == CacheMeta.Type.GET || operation.meta.type == CacheMeta.Type.PUT) {
                            if (method.isVoid()) {
                                throw IllegalArgumentException("$annotationNames annotation can't return Void type, but was for ${operation.meta.origin}")
                            }

                            if (method.isMono()) {
                                throw IllegalArgumentException("$annotationNames annotation doesn't support return type ${method.returnType} in ${operation.meta.origin}")
                            } else if (method.isFuture()) {
                                throw IllegalArgumentException("$annotationNames annotation doesn't support return type ${method.returnType} in ${operation.meta.origin}")
                            } else if (method.isFlux()) {
                                throw IllegalArgumentException("$annotationNames annotation doesn't support return type ${method.returnType} in ${operation.meta.origin}")
                            } else if (method.isPublisher()) {
                                throw IllegalArgumentException("$annotationNames annotation doesn't support return type ${method.returnType} in ${operation.meta.origin}")
                            } else if (method.isFlow()) {
                                throw IllegalArgumentException("$annotationNames annotation doesn't support return type ${method.returnType} in ${operation.meta.origin}")
                            }
                        }

                        if (!generatedCacheKeys.contains(operation.key.canonicalName())) {
                            val parameters = method.getParameters(operation.meta.parameters)
                            val methodParameters = operation.meta.getParametersNames(method).joinToString(",")
                            val toStringParameters = parameters
                                .map { a -> "$" + a.name!!.getShortName() }
                                .joinToString("-", "\"", "\"")

                            var keyBuilder = TypeSpec.classBuilder(operation.key.simpleName)
                                .addSuperinterface(CacheKey::class)
                                .addModifiers(KModifier.DATA)
                                .addAnnotation(AnnotationSpec.builder(Generated::class)
                                    .addMember("\"%L\"", this.javaClass.canonicalName)
                                    .build())
                                .addFunction(
                                    FunSpec.builder("toString")
                                        .addModifiers(KModifier.OVERRIDE)
                                        .addCode(CodeBlock.of("return %L", toStringParameters))
                                        .build()
                                )
                                .addFunction(
                                    FunSpec.builder("values")
                                        .addModifiers(KModifier.OVERRIDE)
                                        .addCode(CodeBlock.of("return mutableListOf(%L)", methodParameters))
                                        .build()
                                )

                            var constructorBuilder = FunSpec.constructorBuilder()
                            for (parameter in parameters) {
                                val paramName = parameter.name!!.getShortName()
                                val typeName = parameter.type.resolve().toClassName().copy(nullable = true)
                                constructorBuilder = constructorBuilder
                                    .addParameter(paramName, typeName)

                                keyBuilder = keyBuilder
                                    .addProperty(
                                        PropertySpec.builder(paramName, typeName)
                                            .initializer(paramName)
                                            .build()
                                    )
                            }
                            keyBuilder.primaryConstructor(constructorBuilder.build())

                            val fileSpec = FileSpec.builder(operation.key.packageName, operation.key.simpleName)
                                .addType(keyBuilder.build())
                                .build()

                            fileSpec.writeTo(codeGenerator = environment.codeGenerator, aggregating = false)
                            generatedCacheKeys.add(operation.key.canonicalName())
                        }
                    } catch (e: IOException) {
                        throw ProcessingErrorException(ProcessingError(e.message.toString(), it));
                    }
                }
            }
        }

        return symbols.filterNot { it.validate() }.toList()
    }
}

