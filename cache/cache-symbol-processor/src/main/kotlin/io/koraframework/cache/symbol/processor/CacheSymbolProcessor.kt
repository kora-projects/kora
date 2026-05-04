package io.koraframework.cache.symbol.processor

import com.google.devtools.ksp.getAllSuperTypes
import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo
import io.koraframework.ksp.common.AnnotationUtils.findAnnotation
import io.koraframework.ksp.common.AnnotationUtils.findAnnotations
import io.koraframework.ksp.common.AnnotationUtils.findValue
import io.koraframework.ksp.common.AnnotationUtils.findValueNoDefault
import io.koraframework.ksp.common.BaseSymbolProcessor
import io.koraframework.ksp.common.CommonAopUtils.extendsKeepAop
import io.koraframework.ksp.common.CommonClassNames
import io.koraframework.ksp.common.CommonClassNames.configValueExtractor
import io.koraframework.ksp.common.KspCommonUtils.addOriginatingKSFile
import io.koraframework.ksp.common.KspCommonUtils.generated
import io.koraframework.ksp.common.KspCommonUtils.toTypeName
import io.koraframework.ksp.common.TagUtils.addTag
import io.koraframework.ksp.common.TagUtils.parseTag
import io.koraframework.ksp.common.TagUtils.toTagAnnotation
import io.koraframework.ksp.common.exception.ProcessingErrorException
import io.koraframework.ksp.common.generatedClass
import io.koraframework.ksp.common.getOuterClassesAsPrefix
import java.util.*

class CacheSymbolProcessor(
    private val environment: SymbolProcessorEnvironment
) : BaseSymbolProcessor(environment) {

    companion object {
        private val ANNOTATION_CACHE = ClassName("io.koraframework.cache.annotation", "Cache")

        private val CAFFEINE_CACHE = ClassName("io.koraframework.cache.caffeine", "CaffeineCache")
        private val CAFFEINE_CACHE_FACTORY = ClassName("io.koraframework.cache.caffeine", "CaffeineCacheFactory")
        private val CAFFEINE_CACHE_CONFIG = ClassName("io.koraframework.cache.caffeine", "CaffeineCacheConfig")
        private val CAFFEINE_CACHE_IMPL = ClassName("io.koraframework.cache.caffeine", "AbstractCaffeineCache")

        private val REDIS_TELEMETRY_FACTORY = ClassName("io.koraframework.cache.redis.telemetry", "RedisCacheTelemetryFactory")
        private val REDIS_CACHE = ClassName("io.koraframework.cache.redis", "RedisCache")
        private val REDIS_CACHE_IMPL = ClassName("io.koraframework.cache.redis", "AbstractRedisCache")
        private val REDIS_CACHE_CONFIG = ClassName("io.koraframework.cache.redis", "RedisCacheConfig")
        private val REDIS_CACHE_CLIENT = ClassName("io.koraframework.cache.redis", "RedisCacheClient")
        private val REDIS_CACHE_MAPPER_KEY = ClassName("io.koraframework.cache.redis", "RedisCacheKeyMapper")
        private val REDIS_CACHE_MAPPER_VALUE = ClassName("io.koraframework.cache.redis", "RedisCacheValueMapper")
    }

    override fun processRound(resolver: Resolver): List<KSAnnotated> {
        val symbols = resolver.getSymbolsWithAnnotation(ANNOTATION_CACHE.canonicalName).toList()
        val symbolsToProcess = symbols.filter { it.validateAll() }.filterIsInstance<KSClassDeclaration>()
        for (cacheImpl in symbolsToProcess) {
            if (cacheImpl.classKind != ClassKind.INTERFACE) {
                throw ProcessingErrorException(
                    "@Cache annotation is intended to be used on interfaces, but was: ${cacheImpl.classKind}",
                    cacheImpl
                )
            }

            val cacheContractType = getCacheSuperType(cacheImpl) ?: continue

            val cacheImplBase = getCacheImplBase(cacheContractType)
            val cacheImplName = getCacheImplName(cacheImpl)
            val implSpec = cacheImpl.extendsKeepAop(cacheImplName.simpleName, resolver)
                .generated(CacheSymbolProcessor::class)
                .primaryConstructor(getCacheConstructor(cacheContractType))
                .addSuperclassConstructorParameter(getCacheSuperConstructorCall(cacheImpl, cacheContractType))
                .superclass(cacheImplBase)
                .build()

            val fileImplSpec = FileSpec.builder(cacheImpl.packageName.asString(), implSpec.name.toString())
                .addType(implSpec)
                .build()
            fileImplSpec.writeTo(codeGenerator = environment.codeGenerator, aggregating = false)

            val cacheModuleName = getCacheModuleName(cacheImpl)
            val moduleSpecBuilder =
                TypeSpec.interfaceBuilder(cacheModuleName)
                    .generated(CacheSymbolProcessor::class)
                    .addOriginatingKSFile(cacheImpl)
                    .addAnnotation(CommonClassNames.module)
                    .addFunction(getCacheMethodImpl(cacheImpl, cacheContractType))
                    .addFunction(getCacheMethodConfig(cacheImpl, cacheContractType, resolver))

            if (cacheContractType.rawType == REDIS_CACHE) {
                val superTypes = cacheImpl.superTypes.toList()
                val superType = superTypes[superTypes.size - 1]

                val keyType = superType.resolve().arguments[0]
                val declaration = keyType.type!!.resolve()
                if (declaration.declaration is KSClassDeclaration && declaration.declaration.modifiers.contains(Modifier.DATA)) {
                    moduleSpecBuilder.addFunction(getCacheRedisKeyMapperForData(cacheImpl, declaration.declaration as KSClassDeclaration))
                }
            }

            val moduleSpec = moduleSpecBuilder.build()

            val fileModuleSpec = FileSpec.builder(cacheImpl.packageName.asString(), moduleSpec.name.toString())
                .addType(moduleSpec)
                .build()
            fileModuleSpec.writeTo(codeGenerator = environment.codeGenerator, aggregating = false)
        }

        return symbols.filterNot { it.validate() }.toList()
    }

    fun findTypedInterface(candidate: KSClassDeclaration, targetFqn: ClassName): KSType? {
        val queue = ArrayDeque<KSType>()
        val visited = mutableSetOf<String>()
        var visitedTypes = mutableSetOf<KSType>()

        candidate.superTypes.forEach { typeRef ->
            val resolved = typeRef.resolve()
            if (resolved.declaration is KSClassDeclaration) {
                queue.add(resolved)
            }
        }

        while (queue.isNotEmpty()) {
            val currentType = queue.removeFirst()
            val currentDecl = currentType.declaration as? KSClassDeclaration ?: continue

            val signature = currentType.toString()
            if (visited.contains(signature)) {
                continue
            }

            if (currentDecl.toClassName() == targetFqn) {
                return if (visitedTypes.isEmpty()) {
                    currentType
                } else {
                    currentType.replace(visitedTypes.asSequence().flatMap { it.arguments }.toList())
                }
            }

            visited.add(signature)
            visitedTypes.add(currentType)
            currentDecl.superTypes.forEach { superTypeRef ->
                val resolvedSuper = try {
                    superTypeRef.resolve()
                } catch (e: Exception) {
                    null
                }

                if (resolvedSuper != null && resolvedSuper.declaration is KSClassDeclaration) {
                    queue.add(resolvedSuper)
                }
            }
        }

        return null
    }

    private fun getCacheSuperType(candidate: KSClassDeclaration): ParameterizedTypeName? {
        val caffeineCache = findTypedInterface(candidate, CAFFEINE_CACHE)
        val redisCache = findTypedInterface(candidate, REDIS_CACHE)

        if (caffeineCache != null && redisCache != null) {
            throw ProcessingErrorException(
                "@Cache annotated interface can't implement both: $REDIS_CACHE and $CAFFEINE_CACHE interfaces",
                candidate
            )
        }

        if (caffeineCache != null) {
            return caffeineCache.toTypeName() as ParameterizedTypeName
        } else if (redisCache != null) {
            return redisCache.toTypeName() as ParameterizedTypeName
        }

        throw ProcessingErrorException(
            "@Cache is expected to be known super type $REDIS_CACHE or $CAFFEINE_CACHE",
            candidate
        )
    }

    private fun getCacheImplBase(cacheType: ParameterizedTypeName): TypeName {
        return when (cacheType.rawType) {
            CAFFEINE_CACHE -> CAFFEINE_CACHE_IMPL.parameterizedBy(cacheType.typeArguments)
            REDIS_CACHE -> REDIS_CACHE_IMPL.parameterizedBy(cacheType.typeArguments)
            else ->
                throw IllegalArgumentException("Unknown cache type: ${cacheType.rawType}")
        }
    }

    private fun getCacheMethodConfig(
        cacheImpl: KSClassDeclaration,
        cacheType: ParameterizedTypeName,
        resolver: Resolver
    ): FunSpec {
        val prefix = cacheImpl.getOuterClassesAsPrefix().substring(1) + cacheImpl.simpleName.asString()
        val methodName = "${prefix.replaceFirstChar { it.lowercaseChar() }}_Config"

        val configPath = cacheImpl.findAnnotations(ANNOTATION_CACHE)
            .mapNotNull { a -> a.findValue<String>("value") }
            .first()

        val returnType = when (cacheType.rawType) {
            CAFFEINE_CACHE -> resolver.getClassDeclarationByName(CAFFEINE_CACHE_CONFIG.canonicalName)!!
            REDIS_CACHE -> resolver.getClassDeclarationByName(REDIS_CACHE_CONFIG.canonicalName)!!
            else -> throw IllegalArgumentException("Unknown cache type: ${cacheType.rawType}")
        }
        val extractorType = configValueExtractor.parameterizedBy(returnType.asType(listOf()).toTypeName())

        return FunSpec.builder(methodName)
            .addAnnotation(cacheImpl.toClassName().toTagAnnotation())
            .addModifiers(KModifier.PUBLIC)
            .addParameter("config", CommonClassNames.config)
            .addParameter("extractor", extractorType)
            .addStatement("return extractor.extract(config.get(%S))!!", configPath)
            .returns(returnType.asType(listOf()).toTypeName())
            .build()
    }

    private fun getCacheImplName(cacheImpl: KSClassDeclaration): ClassName {
        val cacheImplName = cacheImpl.toClassName()
        return ClassName(cacheImplName.packageName, cacheImpl.generatedClass("Impl"))
    }

    private fun getCacheModuleName(cacheImpl: KSClassDeclaration): ClassName {
        val cacheImplName = cacheImpl.toClassName()
        return ClassName(cacheImplName.packageName, cacheImpl.generatedClass("Module"))
    }

    private fun getCacheMethodImpl(
        cacheImpl: KSClassDeclaration,
        cacheContract: ParameterizedTypeName
    ): FunSpec {
        val cacheImplName = getCacheImplName(cacheImpl)
        val cacheTypeName = cacheImpl.toTypeName()
        val prefix = cacheImpl.getOuterClassesAsPrefix().substring(1) + cacheImpl.simpleName.asString()
        val methodName = "${prefix.replaceFirstChar { it.lowercaseChar() }}_Impl"
        return when (cacheContract.rawType) {
            CAFFEINE_CACHE -> {
                FunSpec.builder(methodName)
                    .addModifiers(KModifier.PUBLIC)
                    .addParameter(
                        ParameterSpec.builder("config", CAFFEINE_CACHE_CONFIG)
                            .addAnnotation(cacheTypeName.toTagAnnotation())
                            .build()
                    )
                    .addParameter("factory", CAFFEINE_CACHE_FACTORY)
                    .addStatement("return %T(config, factory)", cacheImplName)
                    .returns(cacheTypeName)
                    .build()
            }

            REDIS_CACHE -> {
                val keyType = cacheContract.typeArguments[0]
                val valueType = cacheContract.typeArguments[1]
                val keyMapperType = REDIS_CACHE_MAPPER_KEY.parameterizedBy(keyType)
                val valueMapperType = REDIS_CACHE_MAPPER_VALUE.parameterizedBy(valueType)

                val cacheContractType = cacheImpl.getAllSuperTypes()
                    .filter { i -> i.toTypeName() == cacheContract }
                    .first()

                val keyMapperBuilder = ParameterSpec.builder("keyMapper", keyMapperType)
                val keyTag = cacheContractType.arguments[0].parseTag()
                keyMapperBuilder.addTag(keyTag)

                val valueMapperBuilder = ParameterSpec.builder("valueMapper", valueMapperType)
                val valueTags = cacheContractType.arguments[1].parseTag()
                valueMapperBuilder.addTag(valueTags)

                FunSpec.builder(methodName)
                    .addModifiers(KModifier.PUBLIC)
                    .addParameter(
                        ParameterSpec.builder("config", REDIS_CACHE_CONFIG)
                            .addAnnotation(cacheTypeName.toTagAnnotation())
                            .build()
                    )
                    .addParameter("redisClient", REDIS_CACHE_CLIENT)
                    .addParameter("telemetryFactory", REDIS_TELEMETRY_FACTORY)
                    .addParameter(keyMapperBuilder.build())
                    .addParameter(valueMapperBuilder.build())
                    .addStatement("return %L(config, redisClient, telemetryFactory, keyMapper, valueMapper)", cacheImplName)
                    .returns(cacheTypeName)
                    .build()
            }

            else -> {
                throw IllegalArgumentException("Unknown cache type: ${cacheContract.rawType}")
            }
        }
    }

    private fun getCacheConstructor(cacheContract: ParameterizedTypeName): FunSpec {
        return when (cacheContract.rawType) {
            CAFFEINE_CACHE -> {
                FunSpec.constructorBuilder()
                    .addParameter("config", CAFFEINE_CACHE_CONFIG)
                    .addParameter("factory", CAFFEINE_CACHE_FACTORY)
                    .build()
            }

            REDIS_CACHE -> {
                val keyType = cacheContract.typeArguments[0]
                val valueType = cacheContract.typeArguments[1]
                val keyMapperType = REDIS_CACHE_MAPPER_KEY.parameterizedBy(keyType)
                val valueMapperType = REDIS_CACHE_MAPPER_VALUE.parameterizedBy(valueType)
                FunSpec.constructorBuilder()
                    .addParameter("config", REDIS_CACHE_CONFIG)
                    .addParameter("redisClient", REDIS_CACHE_CLIENT)
                    .addParameter("telemetryFactory", REDIS_TELEMETRY_FACTORY)
                    .addParameter("keyMapper", keyMapperType)
                    .addParameter("valueMapper", valueMapperType)
                    .build()
            }

            else -> {
                throw IllegalArgumentException("Unknown cache type: ${cacheContract.rawType}")
            }
        }
    }

    private fun getCacheRedisKeyMapperForData(cacheImpl: KSClassDeclaration, keyType: KSClassDeclaration): FunSpec {
        val cachePrefix = cacheImpl.simpleName.asString()
        var prefix = keyType.getOuterClassesAsPrefix().substring(1) + keyType.simpleName.asString()
        if (!prefix.startsWith(cachePrefix)) {
            prefix = cachePrefix + "_" + prefix
        }
        val methodName = "${prefix.replaceFirstChar { it.lowercaseChar() }}_RedisKeyMapper"
        val methodBuilder = FunSpec.builder(methodName)
            .addModifiers(KModifier.PUBLIC)
            .addAnnotation(CommonClassNames.defaultComponent)

        val recordFields = keyType.getAllProperties().toList()
        val keyBuilder = CodeBlock.builder()
        val compositeKeyBuilder = CodeBlock.builder()
        val copyBuilder = CodeBlock.builder()

        copyBuilder.addStatement("var offset = 0")
        for (i in recordFields.indices) {
            val recordField = recordFields[i]
            val mapperName = "keyMapper${i + 1}"

            methodBuilder.addParameter(
                mapperName,
                REDIS_CACHE_MAPPER_KEY.parameterizedBy(recordField.type.resolve().makeNotNullable().toTypeName()),
            )

            val keyName = "_key" + (i + 1)
            keyBuilder.addStatement("val %L = %L.apply(key.%L!!)!!", keyName, mapperName, recordField.simpleName.asString())
            if (i == 0) {
                compositeKeyBuilder.add("val _compositeKey = %T(", ByteArray::class)
                for (j in recordFields.indices) {
                    val compKeyName = "_key" + (j + 1)
                    if (j != 0) {
                        compositeKeyBuilder.add(" + %T.DELIMITER.size + %L.size", REDIS_CACHE_MAPPER_KEY, compKeyName)
                    } else {
                        compositeKeyBuilder.add("%L.size", compKeyName)
                    }
                }
                copyBuilder.addStatement(
                    "%T.arraycopy(%L, 0, _compositeKey, 0, %L.size)",
                    System::class.java,
                    keyName,
                    keyName
                )
                copyBuilder.addStatement("offset += %L.size", keyName)
            } else {
                copyBuilder.addStatement(
                    "%T.arraycopy(%T.DELIMITER, 0, _compositeKey, offset, %T.DELIMITER.size)",
                    System::class, REDIS_CACHE_MAPPER_KEY, REDIS_CACHE_MAPPER_KEY
                )
                copyBuilder.addStatement("offset += %T.DELIMITER.size", REDIS_CACHE_MAPPER_KEY)
                copyBuilder.addStatement(
                    "%T.arraycopy(%L, 0, _compositeKey, offset, %L.size)",
                    System::class.java,
                    keyName,
                    keyName
                )
                if (i != recordFields.size - 1) {
                    copyBuilder.addStatement("offset += %L.size", keyName)
                }
            }
        }

        compositeKeyBuilder.addStatement(")")
        copyBuilder.addStatement("_compositeKey")

        return methodBuilder
            .addCode(
                CodeBlock.builder()
                    .beginControlFlow(
                        "return %T { key -> ",
                        REDIS_CACHE_MAPPER_KEY.parameterizedBy(keyType.toClassName())
                    )
                    .add(keyBuilder.build())
                    .add(compositeKeyBuilder.build())
                    .add(copyBuilder.build())
                    .endControlFlow()
                    .build()
            )
            .returns(REDIS_CACHE_MAPPER_KEY.parameterizedBy(keyType.toClassName()))
            .build()
    }

    private fun getCacheSuperConstructorCall(
        cacheImpl: KSClassDeclaration,
        cacheType: ParameterizedTypeName
    ): CodeBlock {
        val configPath = cacheImpl.findAnnotation(ANNOTATION_CACHE)
            ?.findValueNoDefault<String>("value")!!

        return when (cacheType.rawType) {
            CAFFEINE_CACHE -> CodeBlock.of("%S, %S, config, factory", configPath, cacheImpl.qualifiedName!!.asString())
            REDIS_CACHE -> CodeBlock.of("%S, %S, config, redisClient, telemetryFactory, keyMapper, valueMapper", configPath, cacheImpl.qualifiedName!!.asString())
            else -> throw IllegalArgumentException("Unknown cache type: ${cacheType.rawType}")
        }
    }
}

