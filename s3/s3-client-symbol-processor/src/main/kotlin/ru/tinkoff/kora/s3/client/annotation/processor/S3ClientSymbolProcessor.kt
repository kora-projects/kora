package ru.tinkoff.kora.s3.client.annotation.processor

import com.google.devtools.ksp.getAllSuperTypes
import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo
import ru.tinkoff.kora.common.DefaultComponent
import ru.tinkoff.kora.ksp.common.AnnotationUtils.findAnnotation
import ru.tinkoff.kora.ksp.common.AnnotationUtils.findAnnotations
import ru.tinkoff.kora.ksp.common.AnnotationUtils.findValue
import ru.tinkoff.kora.ksp.common.AnnotationUtils.findValueNoDefault
import ru.tinkoff.kora.ksp.common.BaseSymbolProcessor
import ru.tinkoff.kora.ksp.common.CommonClassNames
import ru.tinkoff.kora.ksp.common.CommonClassNames.configValueExtractor
import ru.tinkoff.kora.ksp.common.KspCommonUtils.generated
import ru.tinkoff.kora.ksp.common.KspCommonUtils.toTypeName
import ru.tinkoff.kora.ksp.common.TagUtils.parseTags
import ru.tinkoff.kora.ksp.common.TagUtils.toTagAnnotation

class S3ClientSymbolProcessor(
    private val environment: SymbolProcessorEnvironment
) : BaseSymbolProcessor(environment) {

    companion object {
        private val ANNOTATION_CACHE = ClassName("ru.tinkoff.kora.cache.annotation", "Cache")

        private val CAFFEINE_CACHE = ClassName("ru.tinkoff.kora.cache.caffeine", "CaffeineCache")
        private val CAFFEINE_TELEMETRY = ClassName("ru.tinkoff.kora.cache.caffeine", "CaffeineCacheTelemetry")
        private val CAFFEINE_CACHE_IMPL = ClassName("ru.tinkoff.kora.cache.caffeine", "AbstractCaffeineCache")
        private val CAFFEINE_CACHE_CONFIG = ClassName("ru.tinkoff.kora.cache.caffeine", "CaffeineCacheConfig")
        private val CAFFEINE_CACHE_FACTORY = ClassName("ru.tinkoff.kora.cache.caffeine", "CaffeineCacheFactory")

        private val REDIS_CACHE = ClassName("ru.tinkoff.kora.cache.redis", "RedisCache")
        private val REDIS_TELEMETRY = ClassName("ru.tinkoff.kora.cache.redis", "RedisCacheTelemetry")
        private val REDIS_CACHE_IMPL = ClassName("ru.tinkoff.kora.cache.redis", "AbstractRedisCache")
        private val REDIS_CACHE_CONFIG = ClassName("ru.tinkoff.kora.cache.redis", "RedisCacheConfig")
        private val REDIS_CACHE_CLIENT = ClassName("ru.tinkoff.kora.cache.redis", "RedisCacheClient")
        private val REDIS_CACHE_MAPPER_KEY = ClassName("ru.tinkoff.kora.cache.redis", "RedisCacheKeyMapper")
        private val REDIS_CACHE_MAPPER_VALUE = ClassName("ru.tinkoff.kora.cache.redis", "RedisCacheValueMapper")
    }

    override fun processRound(resolver: Resolver): List<KSAnnotated> {
        val symbols = resolver.getSymbolsWithAnnotation(ANNOTATION_CACHE.canonicalName).toList()
        val symbolsToProcess = symbols.filter { it.validate() }.filterIsInstance<KSClassDeclaration>()
        for (cacheContract in symbolsToProcess) {
            if (cacheContract.classKind != ClassKind.INTERFACE) {
                environment.logger.error(
                    "@Cache annotation is intended to be used on interfaces, but was: ${cacheContract.classKind}",
                    cacheContract
                )
                continue
            }

            val cacheContractType = getCacheSuperType(cacheContract) ?: continue

            val packageName = cacheContract.packageName.asString()
            val cacheImplName = cacheContract.toClassName()

            val cacheImplBase = getCacheImplBase(cacheContractType)
            val implSpec = TypeSpec.classBuilder(getCacheImpl(cacheContract))
                .generated(S3ClientSymbolProcessor::class)
                .primaryConstructor(getCacheConstructor(cacheContractType))
                .addSuperclassConstructorParameter(getCacheSuperConstructorCall(cacheContract, cacheContractType))
                .superclass(cacheImplBase)
                .addSuperinterface(cacheContract.toTypeName())
                .build()

            val fileImplSpec = FileSpec.builder(cacheContract.packageName.asString(), implSpec.name.toString())
                .addType(implSpec)
                .build()
            fileImplSpec.writeTo(codeGenerator = environment.codeGenerator, aggregating = false)

            val moduleSpecBuilder =
                TypeSpec.interfaceBuilder(ClassName(packageName, "$${cacheImplName.simpleName}Module"))
                    .generated(S3ClientSymbolProcessor::class)
                    .addAnnotation(CommonClassNames.module)
                    .addFunction(getCacheMethodImpl(cacheContract, cacheContractType))
                    .addFunction(getCacheMethodConfig(cacheContract, cacheContractType, resolver))

            if (cacheContractType.rawType == REDIS_CACHE) {
                val superTypes = cacheContract.superTypes.toList()
                val superType = superTypes[superTypes.size - 1]

                val keyType = superType.resolve().arguments[0]
                val declaration = keyType.type!!.resolve()
                if (declaration.declaration is KSClassDeclaration && declaration.declaration.modifiers.contains(Modifier.DATA)) {
                    moduleSpecBuilder.addFunction(getCacheRedisKeyMapperForData(declaration.declaration as KSClassDeclaration))
                }
            }

            val moduleSpec = moduleSpecBuilder.build()

            val fileModuleSpec = FileSpec.builder(cacheContract.packageName.asString(), moduleSpec.name.toString())
                .addType(moduleSpec)
                .build()
            fileModuleSpec.writeTo(codeGenerator = environment.codeGenerator, aggregating = false)
        }

        return symbols.filterNot { it.validate() }.toList()
    }

    private fun getCacheSuperType(candidate: KSClassDeclaration): ParameterizedTypeName? {
        val supertypes = candidate.superTypes.toList()
        if (supertypes.size != 1) {
            environment.logger.error(
                "@Cache annotated interface should implement one one interface and it should be one of: $REDIS_CACHE,$CAFFEINE_CACHE",
                candidate
            )
            return null
        }
        val supertype = supertypes[0].toTypeName() as ParameterizedTypeName
        return when (supertype.rawType) {
            CAFFEINE_CACHE -> supertype
            REDIS_CACHE -> supertype
            else -> {
                this.environment.logger.error("@Cache is expected to be known super type $REDIS_CACHE or $CAFFEINE_CACHE, but was $supertype")
                null
            }
        }
    }

    private fun getCacheImplBase(cacheType: ParameterizedTypeName): TypeName {
        if (cacheType.rawType == CAFFEINE_CACHE) {
            return CAFFEINE_CACHE_IMPL.parameterizedBy(cacheType.typeArguments)
        } else if (cacheType.rawType == REDIS_CACHE) {
            return REDIS_CACHE_IMPL.parameterizedBy(cacheType.typeArguments)
        } else {
            throw IllegalArgumentException("Unknown cache type: ${cacheType.rawType}")
        }
    }

    private fun getCacheMethodConfig(
        cacheContract: KSClassDeclaration,
        cacheType: ParameterizedTypeName,
        resolver: Resolver
    ): FunSpec {
        val configPath = cacheContract.findAnnotations(ANNOTATION_CACHE)
            .mapNotNull { a -> a.findValue<String>("value") }
            .first()

        val cacheContractName = cacheContract.toClassName()
        val methodName = "${cacheContractName.simpleName}Config"
        val returnType = when (cacheType.rawType) {
            CAFFEINE_CACHE -> resolver.getClassDeclarationByName(CAFFEINE_CACHE_CONFIG.canonicalName)!!
            REDIS_CACHE -> resolver.getClassDeclarationByName(REDIS_CACHE_CONFIG.canonicalName)!!
            else -> throw IllegalArgumentException("Unknown cache type: ${cacheType.rawType}")
        }
        val extractorType = configValueExtractor.parameterizedBy(returnType.asType(listOf()).toTypeName())

        return FunSpec.builder(methodName)
            .addAnnotation(
                AnnotationSpec.builder(CommonClassNames.tag)
                    .addMember("%T::class", cacheContractName)
                    .build()
            )
            .addModifiers(KModifier.PUBLIC)
            .addParameter("config", CommonClassNames.config)
            .addParameter("extractor", extractorType)
            .addStatement("return extractor.extract(config.get(%S))!!", configPath)
            .returns(returnType.asType(listOf()).toTypeName())
            .build()
    }

    private fun getCacheImpl(cacheContract: KSClassDeclaration): ClassName {
        val cacheImplName = cacheContract.toClassName()
        return ClassName(cacheImplName.packageName, "$${cacheImplName.simpleName}Impl")
    }

    private fun getCacheMethodImpl(cacheClass: KSClassDeclaration, cacheContract: ParameterizedTypeName): FunSpec {
        val cacheImplName = getCacheImpl(cacheClass)
        val cacheTypeName = cacheClass.toTypeName()
        val methodName = "${cacheImplName.simpleName}Impl"
        return when (cacheContract.rawType) {
            CAFFEINE_CACHE -> {
                FunSpec.builder(methodName)
                    .addModifiers(KModifier.PUBLIC)
                    .addParameter(
                        ParameterSpec.builder("config", CAFFEINE_CACHE_CONFIG)
                            .addAnnotation(
                                AnnotationSpec.builder(CommonClassNames.tag)
                                    .addMember("%T::class", cacheTypeName)
                                    .build()
                            )
                            .build()
                    )
                    .addParameter("factory", CAFFEINE_CACHE_FACTORY)
                    .addParameter("telemetry", CAFFEINE_TELEMETRY)
                    .addStatement("return %T(config, factory, telemetry)", cacheImplName)
                    .returns(cacheTypeName)
                    .build()
            }

            REDIS_CACHE -> {
                val keyType = cacheContract.typeArguments[0]
                val valueType = cacheContract.typeArguments[1]
                val keyMapperType = REDIS_CACHE_MAPPER_KEY.parameterizedBy(keyType)
                val valueMapperType = REDIS_CACHE_MAPPER_VALUE.parameterizedBy(valueType)

                val cacheContractType = cacheClass.getAllSuperTypes()
                    .filter { i -> i.toTypeName() == cacheContract }
                    .first()

                val keyMapperBuilder = ParameterSpec.builder("keyMapper", keyMapperType)
                val keyTags = cacheContractType.arguments[0].parseTags()
                if (keyTags.isNotEmpty()) {
                    keyMapperBuilder.addAnnotation(keyTags.toTagAnnotation())
                }

                val valueMapperBuilder = ParameterSpec.builder("valueMapper", valueMapperType)
                val valueTags = cacheContractType.arguments[1].parseTags()
                if (valueTags.isNotEmpty()) {
                    valueMapperBuilder.addAnnotation(valueTags.toTagAnnotation())
                }

                FunSpec.builder(methodName)
                    .addModifiers(KModifier.PUBLIC)
                    .addParameter(
                        ParameterSpec.builder("config", REDIS_CACHE_CONFIG)
                            .addAnnotation(
                                AnnotationSpec.builder(CommonClassNames.tag)
                                    .addMember("%T::class", cacheTypeName)
                                    .build()
                            )
                            .build()
                    )
                    .addParameter("redisClient", REDIS_CACHE_CLIENT)
                    .addParameter("telemetry", REDIS_TELEMETRY)
                    .addParameter(keyMapperBuilder.build())
                    .addParameter(valueMapperBuilder.build())
                    .addStatement("return %L(config, redisClient, telemetry, keyMapper, valueMapper)", cacheImplName)
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
                    .addParameter("telemetry", CAFFEINE_TELEMETRY)
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
                    .addParameter("telemetry", REDIS_TELEMETRY)
                    .addParameter("keyMapper", keyMapperType)
                    .addParameter("valueMapper", valueMapperType)
                    .build()
            }

            else -> {
                throw IllegalArgumentException("Unknown cache type: ${cacheContract.rawType}")
            }
        }
    }

    private fun getCacheRedisKeyMapperForData(keyType: KSClassDeclaration): FunSpec {
        val prefix = keyType.toClassName().simpleNames.joinToString("_")
        val methodName = "${prefix}_RedisKeyMapper"
        val methodBuilder = FunSpec.builder(methodName)
            .addModifiers(KModifier.PUBLIC)
            .addAnnotation(DefaultComponent::class)

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
            keyBuilder.addStatement("val %L = %L.apply(key.%L)!!", keyName, mapperName, recordField.simpleName.asString())
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
        cacheContract: KSClassDeclaration,
        cacheType: ParameterizedTypeName
    ): CodeBlock {
        val configPath = cacheContract.findAnnotation(ANNOTATION_CACHE)
            ?.findValueNoDefault<String>("value")!!

        return when (cacheType.rawType) {
            CAFFEINE_CACHE -> CodeBlock.of("%S, config, factory, telemetry", configPath)
            REDIS_CACHE -> CodeBlock.of("%S, config, redisClient, telemetry, keyMapper, valueMapper", configPath)
            else -> throw IllegalArgumentException("Unknown cache type: ${cacheType.rawType}")
        }
    }
}

