package ru.tinkoff.kora.cache.symbol.processor

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo
import ru.tinkoff.kora.ksp.common.AnnotationUtils.findAnnotation
import ru.tinkoff.kora.ksp.common.AnnotationUtils.findValueNoDefault
import ru.tinkoff.kora.ksp.common.BaseSymbolProcessor
import ru.tinkoff.kora.ksp.common.CommonClassNames
import ru.tinkoff.kora.ksp.common.CommonClassNames.configValueExtractor
import ru.tinkoff.kora.ksp.common.KspCommonUtils.toTypeName

class CacheSymbolProcessor(
    private val environment: SymbolProcessorEnvironment
) : BaseSymbolProcessor(environment) {

    companion object {
        private val ANNOTATION_CACHE = ClassName("ru.tinkoff.kora.cache.annotation", "Cache")

        private val CAFFEINE_TELEMETRY = ClassName("ru.tinkoff.kora.cache.caffeine", "CaffeineCacheTelemetry")
        private val CAFFEINE_CACHE = ClassName("ru.tinkoff.kora.cache.caffeine", "CaffeineCache")
        private val CAFFEINE_CACHE_FACTORY = ClassName("ru.tinkoff.kora.cache.caffeine", "CaffeineCacheFactory")
        private val CAFFEINE_CACHE_CONFIG = ClassName("ru.tinkoff.kora.cache.caffeine", "CaffeineCacheConfig")
        private val CAFFEINE_CACHE_IMPL = ClassName("ru.tinkoff.kora.cache.caffeine", "AbstractCaffeineCache")

        private val REDIS_TELEMETRY = ClassName("ru.tinkoff.kora.cache.redis", "RedisCacheTelemetry")
        private val REDIS_CACHE = ClassName("ru.tinkoff.kora.cache.redis", "RedisCache")
        private val REDIS_CACHE_IMPL = ClassName("ru.tinkoff.kora.cache.redis", "AbstractRedisCache")
        private val REDIS_CACHE_CONFIG = ClassName("ru.tinkoff.kora.cache.redis", "RedisCacheConfig")
        private val REDIS_CACHE_CLIENT_SYNC = ClassName("ru.tinkoff.kora.cache.redis.client", "SyncRedisClient")
        private val REDIS_CACHE_CLIENT_REACTIVE = ClassName("ru.tinkoff.kora.cache.redis.client", "ReactiveRedisClient")
        private val REDIS_CACHE_MAPPER_KEY = ClassName("ru.tinkoff.kora.cache.redis", "RedisCacheKeyMapper")
        private val REDIS_CACHE_MAPPER_VALUE = ClassName("ru.tinkoff.kora.cache.redis", "RedisCacheValueMapper")
    }

    override fun processRound(resolver: Resolver): List<KSAnnotated> {
        val symbols = resolver.getSymbolsWithAnnotation(ANNOTATION_CACHE.canonicalName).toList()
        val symbolsToProcess = symbols.filter { it.validate() }.filterIsInstance<KSClassDeclaration>()
        for (cacheContract in symbolsToProcess) {
            if (cacheContract.classKind != ClassKind.INTERFACE) {
                environment.logger.error("@Cache annotation is intended to be used on interfaces, but was: ${cacheContract.classKind}", cacheContract)
                continue
            }

            val cacheContractType = getCacheSuperType(cacheContract) ?: continue

            val packageName = cacheContract.packageName.asString()
            val cacheImplName = cacheContract.toClassName()

            val cacheImplBase = getCacheImplBase(cacheContractType)
            val implSpec = TypeSpec.classBuilder(getCacheImpl(cacheContract))
                .addAnnotation(
                    AnnotationSpec.builder(CommonClassNames.generated)
                        .addMember(CodeBlock.of("%S", CacheSymbolProcessor::class.java.canonicalName)).build()
                )
                .primaryConstructor(getCacheConstructor(cacheContractType))
                .addSuperclassConstructorParameter(getCacheSuperConstructorCall(cacheContract, cacheContractType))
                .superclass(cacheImplBase)
                .addSuperinterface(cacheContract.toTypeName())
                .build()

            val fileImplSpec = FileSpec.builder(cacheContract.packageName.asString(), implSpec.name.toString())
                .addType(implSpec)
                .build()
            fileImplSpec.writeTo(codeGenerator = environment.codeGenerator, aggregating = false)

            val moduleSpec = TypeSpec.interfaceBuilder(ClassName(packageName, "$${cacheImplName.simpleName}Module"))
                .addAnnotation(
                    AnnotationSpec.builder(CommonClassNames.generated)
                        .addMember(CodeBlock.of("%S", CacheSymbolProcessor::class.java.canonicalName)).build()
                )
                .addAnnotation(CommonClassNames.module)
                .addFunction(getCacheMethodImpl(cacheContract, cacheContractType))
                .addFunction(getCacheMethodConfig(cacheContract, cacheContractType, resolver))
                .build()

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
            environment.logger.error("@Cache annotated interface should implement one one interface and it should be one of: ${REDIS_CACHE},${CAFFEINE_CACHE}", candidate)
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

    private fun getCacheMethodConfig(cacheContract: KSClassDeclaration, cacheType: ParameterizedTypeName, resolver: Resolver): FunSpec {
        val configPath = cacheContract.annotations
            .filter { a -> a.annotationType.resolve().toClassName() == ANNOTATION_CACHE }
            .flatMap { a -> a.arguments }
            .filter { arg -> arg.name!!.getShortName() == "value" }
            .map { arg -> arg.value as String }
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
                    .addMember(cacheContractName.simpleName + "::class")
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

    private fun getCacheMethodImpl(cacheImpl: KSClassDeclaration, cacheContract: ParameterizedTypeName): FunSpec {
        val cacheImplName = getCacheImpl(cacheImpl)
        val methodName = "${cacheImplName.simpleName}Impl"
        return when (cacheContract.rawType) {
            CAFFEINE_CACHE -> {
                FunSpec.builder(methodName)
                    .addModifiers(KModifier.PUBLIC)
                    .addParameter(
                        ParameterSpec.builder("config", CAFFEINE_CACHE_CONFIG)
                            .addAnnotation(
                                AnnotationSpec.builder(CommonClassNames.tag)
                                    .addMember("${cacheImpl.simpleName.getShortName()}::class")
                                    .build()
                            )
                            .build()
                    )
                    .addParameter("factory", CAFFEINE_CACHE_FACTORY)
                    .addParameter("telemetry", CAFFEINE_TELEMETRY)
                    .addStatement("return %T(config, factory, telemetry)", cacheImplName)
                    .returns(cacheImpl.toTypeName())
                    .build()
            }
            REDIS_CACHE -> {
                val keyType = cacheContract.typeArguments[0]
                val valueType = cacheContract.typeArguments[1]
                val keyMapperType = REDIS_CACHE_MAPPER_KEY.parameterizedBy(keyType)
                val valueMapperType = REDIS_CACHE_MAPPER_VALUE.parameterizedBy(valueType)
                FunSpec.builder(methodName)
                    .addModifiers(KModifier.PUBLIC)
                    .addParameter(
                        ParameterSpec.builder("config", REDIS_CACHE_CONFIG)
                            .addAnnotation(
                                AnnotationSpec.builder(CommonClassNames.tag)
                                    .addMember("%T::class", cacheImpl.toTypeName())
                                    .build()
                            )
                            .build()
                    )
                    .addParameter("syncClient", REDIS_CACHE_CLIENT_SYNC)
                    .addParameter("reactiveClient", REDIS_CACHE_CLIENT_REACTIVE)
                    .addParameter("telemetry", REDIS_TELEMETRY)
                    .addParameter("keyMapper", keyMapperType)
                    .addParameter("valueMapper", valueMapperType)
                    .addStatement("return %L(config, syncClient, reactiveClient, telemetry, keyMapper, valueMapper)", cacheImplName)
                    .returns(cacheImpl.toTypeName())
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
                    .addParameter("syncClient", REDIS_CACHE_CLIENT_SYNC)
                    .addParameter("reactiveClient", REDIS_CACHE_CLIENT_REACTIVE)
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

    private fun getCacheSuperConstructorCall(cacheContract: KSClassDeclaration, cacheType: ParameterizedTypeName): CodeBlock {
        val configPath = cacheContract.findAnnotation(ANNOTATION_CACHE)
            ?.findValueNoDefault<String>("value")!!

        return when (cacheType.rawType) {
            CAFFEINE_CACHE -> CodeBlock.of("%S, config, factory, telemetry", configPath)
            REDIS_CACHE -> CodeBlock.of("%S, config, syncClient, reactiveClient, telemetry, keyMapper, valueMapper", configPath)
            else -> throw IllegalArgumentException("Unknown cache type: ${cacheType.rawType}")
        }
    }

    private fun getPackage(element: KSAnnotated): String {
        return element.toString()
    }
}

