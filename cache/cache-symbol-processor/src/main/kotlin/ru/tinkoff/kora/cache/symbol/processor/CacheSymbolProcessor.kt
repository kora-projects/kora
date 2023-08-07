package ru.tinkoff.kora.cache.symbol.processor

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSTypeReference
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.toTypeVariableName
import com.squareup.kotlinpoet.ksp.writeTo
import ru.tinkoff.kora.common.Module
import ru.tinkoff.kora.common.Tag
import ru.tinkoff.kora.ksp.common.BaseSymbolProcessor
import ru.tinkoff.kora.ksp.common.CommonClassNames
import ru.tinkoff.kora.ksp.common.KspCommonUtils.toTypeName
import ru.tinkoff.kora.ksp.common.visitClass

@KspExperimental
class CacheSymbolProcessor(
    private val environment: SymbolProcessorEnvironment
) : BaseSymbolProcessor(environment) {

    private val ANNOTATION_CACHE = ClassName("ru.tinkoff.kora.cache.annotation", "Cache")

    private val CLASS_CONFIG = ClassName("ru.tinkoff.kora.config.common", "Config")
    private val CLASS_CONFIG_EXTRACTOR = ClassName("ru.tinkoff.kora.config.common.extractor", "ConfigValueExtractor")

    private val CAFFEINE_TELEMETRY = ClassName("ru.tinkoff.kora.cache.caffeine", "CaffeineCacheTelemetry")
    private val CAFFEINE_CACHE = ClassName("ru.tinkoff.kora.cache.caffeine", "CaffeineCache")
    private val CAFFEINE_CACHE_FACTORY = ClassName("ru.tinkoff.kora.cache.caffeine", "CaffeineCacheFactory")
    private val CAFFEINE_CACHE_CONFIG = ClassName("ru.tinkoff.kora.cache.caffeine", "CaffeineCacheConfig")
    private val CAFFEINE_CACHE_IMPL = ClassName("ru.tinkoff.kora.cache.caffeine", "AbstractCaffeineCache")

    private val REDIS_TELEMETRY = ClassName("ru.tinkoff.kora.cache.redis", "RedisCacheTelemetry")
    private val REDIS_CACHE = ClassName("ru.tinkoff.kora.cache.redis", "RedisCache")
    private val REDIS_CACHE_IMPL = ClassName("ru.tinkoff.kora.cache.redis", "AbstractRedisCache")
    private val REDIS_CACHE_CONFIG = ClassName("ru.tinkoff.kora.cache.redis", "RedisCacheConfig")
    private val REDIS_CACHE_CLIENT_SYNC = ClassName("ru.tinkoff.kora.cache.redis", "SyncRedisClient")
    private val REDIS_CACHE_CLIENT_REACTIVE = ClassName("ru.tinkoff.kora.cache.redis", "ReactiveRedisClient")
    private val REDIS_CACHE_MAPPER_KEY = ClassName("ru.tinkoff.kora.cache.redis", "RedisCacheKeyMapper")
    private val REDIS_CACHE_MAPPER_VALUE = ClassName("ru.tinkoff.kora.cache.redis", "RedisCacheValueMapper")

    override fun processRound(resolver: Resolver): List<KSAnnotated> {
        val symbols = resolver.getSymbolsWithAnnotation(ANNOTATION_CACHE.canonicalName).toList()

        val symbolsToProcess = symbols.filter { it.validate() }
        symbolsToProcess.forEach {
            it.visitClass { cacheContract ->

                require(cacheContract.classKind == ClassKind.INTERFACE) { "@Cache annotation is intended to be used on interfaces, but was: ${cacheContract.classKind}" }

                val cacheContractType = getCacheSuperType(cacheContract, resolver)
                require(cacheContractType != null) {
                    ("@Cache is expected to be known super type "
                        + CAFFEINE_CACHE.canonicalName
                        + " or "
                        + REDIS_CACHE.canonicalName
                        + ", but was: " + cacheContract.superTypes.first())
                }

                val packageName = getPackage(cacheContract)
                val cacheImplName = cacheContract.toClassName()

                val cacheImplBase = getCacheImplBase(cacheContract, cacheContractType, resolver)
                val implSpec = TypeSpec.classBuilder(getCacheImpl(cacheContract))
                    .addAnnotation(
                        AnnotationSpec.builder(CommonClassNames.generated)
                            .addMember(CodeBlock.of("%S", CacheSymbolProcessor::class.java.canonicalName)).build()
                    )
                    .primaryConstructor(getCacheConstructor(cacheContract, cacheContractType))
                    .addSuperclassConstructorParameter(getCacheSuperConstructorCall(cacheContract, cacheContractType))
                    .superclass(cacheImplBase)
                    .addSuperinterface(cacheContract.toTypeName())
                    .build()

                val fileImplSpec = FileSpec.builder(cacheContract.packageName.asString(), implSpec.name.toString())
                    .addType(implSpec)
                    .build()
                fileImplSpec.writeTo(codeGenerator = environment.codeGenerator, aggregating = false)

                val moduleSpec: TypeSpec = TypeSpec.interfaceBuilder(ClassName(packageName, "$${cacheImplName.simpleName}Module"))
                    .addAnnotation(
                        AnnotationSpec.builder(CommonClassNames.generated)
                            .addMember(CodeBlock.of("%S", CacheSymbolProcessor::class.java.canonicalName)).build()
                    )
                    .addAnnotation(Module::class)
                    .addFunction(getCacheMethodImpl(cacheContract, cacheContractType))
                    .addFunction(getCacheMethodConfig(cacheContract, cacheContractType, resolver))
                    .build()

                val fileModuleSpec = FileSpec.builder(cacheContract.packageName.asString(), moduleSpec.name.toString())
                    .addType(moduleSpec)
                    .build()
                fileModuleSpec.writeTo(codeGenerator = environment.codeGenerator, aggregating = false)
            }
        }

        return symbols.filterNot { it.validate() }.toList()
    }

    private fun getCacheSuperType(candidate: KSClassDeclaration, resolver: Resolver): KSTypeReference? {
        val caffeineElement = resolver.getClassDeclarationByName(CAFFEINE_CACHE.canonicalName)?.asType(listOf())
        if (caffeineElement != null) {
            val superType = candidate.superTypes.filter { t -> t.resolve().toClassName() == caffeineElement.toClassName() }
                .firstOrNull()

            if (superType != null) {
                return superType
            }
        }

        val redisElement = resolver.getClassDeclarationByName(REDIS_CACHE.canonicalName)?.asType(listOf())
        if (redisElement != null) {
            val superType = candidate.superTypes.filter { t -> t.resolve().toClassName() == redisElement.toClassName() }
                .firstOrNull()

            if (superType != null) {
                return superType
            }
        }

        return null
    }

    private fun getCacheImplBase(cacheContract: KSClassDeclaration, cacheType: KSTypeReference, resolver: Resolver): TypeName {
        val resolved = cacheType.resolve()
        return if (resolved.toClassName() == CAFFEINE_CACHE) {
            resolver.getClassDeclarationByName(CAFFEINE_CACHE_IMPL.canonicalName)!!.asType(cacheType.resolve().arguments).toTypeName()
        } else if (resolved.toClassName() == REDIS_CACHE) {
            resolver.getClassDeclarationByName(REDIS_CACHE_IMPL.canonicalName)!!.asType(cacheType.resolve().arguments).toTypeName()
        } else {
            throw UnsupportedOperationException("Unknown implementation: " + cacheContract.toClassName())
        }
    }

    private fun getCacheMethodConfig(cacheContract: KSClassDeclaration, cacheType: KSTypeReference, resolver: Resolver): FunSpec {
        val configPath = cacheContract.annotations
            .filter { a -> a.annotationType.resolve().toClassName() == ANNOTATION_CACHE }
            .flatMap { a -> a.arguments }
            .filter { arg -> arg.name!!.getShortName() == "value" }
            .map { arg -> arg.value as String }
            .first()

        val cacheContractName = cacheContract.toClassName()
        val methodName = "${cacheContractName.simpleName}Config"
        val extractorType: ParameterizedTypeName
        val returnType: KSClassDeclaration
        val resolved = cacheType.resolve()
        if (resolved.toClassName() == CAFFEINE_CACHE) {
            returnType = resolver.getClassDeclarationByName(CAFFEINE_CACHE_CONFIG.canonicalName)!!
            extractorType = CLASS_CONFIG_EXTRACTOR.parameterizedBy(returnType.asType(listOf()).toTypeName())
        } else if (resolved.toClassName() == REDIS_CACHE) {
            returnType = resolver.getClassDeclarationByName(REDIS_CACHE_CONFIG.canonicalName)!!
            extractorType = CLASS_CONFIG_EXTRACTOR.parameterizedBy(returnType.asType(listOf()).toTypeName())
        } else {
            throw UnsupportedOperationException("Unknown implementation: $cacheContract")
        }

        return FunSpec.builder(methodName)
            .addAnnotation(
                AnnotationSpec.builder(Tag::class)
                    .addMember(cacheContractName.simpleName + "::class")
                    .build()
            )
            .addModifiers(KModifier.PUBLIC)
            .addParameter("config", CLASS_CONFIG)
            .addParameter("extractor", extractorType)
            .addStatement("return extractor.extract(config.get(%S))!!", configPath)
            .returns(returnType.asType(listOf()).toTypeName())
            .build()
    }

    private fun getCacheImpl(cacheContract: KSClassDeclaration): ClassName {
        val cacheImplName = cacheContract.toClassName()
        return ClassName(cacheImplName.packageName, "$${cacheImplName.simpleName}Impl")
    }

    private fun getCacheMethodImpl(cacheContract: KSClassDeclaration, cacheType: KSTypeReference): FunSpec {
        val cacheImplName = getCacheImpl(cacheContract)
        val methodName = "${cacheImplName.simpleName}Impl"
        val resolved = cacheType.resolve()
        return if (resolved.toClassName() == CAFFEINE_CACHE) {
            FunSpec.builder(methodName)
                .addModifiers(KModifier.PUBLIC)
                .addParameter(
                    ParameterSpec.builder("config", CAFFEINE_CACHE_CONFIG)
                        .addAnnotation(
                            AnnotationSpec.builder(Tag::class)
                                .addMember("${cacheContract.simpleName.getShortName()}::class")
                                .build()
                        )
                        .build()
                )
                .addParameter("factory", CAFFEINE_CACHE_FACTORY)
                .addParameter("telemetry", CAFFEINE_TELEMETRY)
                .addStatement("return %T(config, factory, telemetry)", cacheImplName)
                .returns(cacheContract.toTypeName())
                .build()
        } else if (resolved.toClassName() == REDIS_CACHE) {
            val keyType = cacheContract.typeParameters[0]
            val valueType = cacheContract.typeParameters[1]
            val keyMapperType = REDIS_CACHE_MAPPER_KEY.parameterizedBy(keyType.toTypeVariableName())
            val valueMapperType = REDIS_CACHE_MAPPER_VALUE.parameterizedBy(valueType.toTypeVariableName())
            FunSpec.builder(methodName)
                .addModifiers(KModifier.PUBLIC)
                .addParameter(
                    ParameterSpec.builder("config", REDIS_CACHE_CONFIG)
                        .addAnnotation(
                            AnnotationSpec.builder(Tag::class)
                                .addMember("${cacheContract.simpleName.getShortName()}::class")
                                .build()
                        )
                        .build()
                )
                .addParameter("syncClient", REDIS_CACHE_CLIENT_SYNC)
                .addParameter("reactiveClient", REDIS_CACHE_CLIENT_REACTIVE)
                .addParameter("telemetry", REDIS_TELEMETRY)
                .addParameter("keyMapper", keyMapperType)
                .addParameter("valueMapper", valueMapperType)
                .addStatement("return new %L(config, syncClient, reactiveClient, telemetry, keyMapper, valueMapper)", methodName)
                .returns(cacheContract.toTypeName())
                .build()
        } else {
            throw UnsupportedOperationException("Unknown implementation: $cacheContract")
        }
    }

    private fun getCacheConstructor(cacheContract: KSClassDeclaration, cacheType: KSTypeReference): FunSpec {
        val resolved = cacheType.resolve()
        return if (resolved.toClassName() == CAFFEINE_CACHE) {
            FunSpec.constructorBuilder()
                .addParameter("config", CAFFEINE_CACHE_CONFIG)
                .addParameter("factory", CAFFEINE_CACHE_FACTORY)
                .addParameter("telemetry", CAFFEINE_TELEMETRY)
                .build()
        } else if (resolved.toClassName() == REDIS_CACHE) {
            val keyType = cacheContract.typeParameters[0]
            val valueType = cacheContract.typeParameters[1]
            val keyMapperType = REDIS_CACHE_MAPPER_KEY.parameterizedBy(keyType.toTypeVariableName())
            val valueMapperType = REDIS_CACHE_MAPPER_VALUE.parameterizedBy(valueType.toTypeVariableName())
            FunSpec.constructorBuilder()
                .addParameter("config", REDIS_CACHE_CONFIG)
                .addParameter("syncClient", REDIS_CACHE_CLIENT_SYNC)
                .addParameter("reactiveClient", REDIS_CACHE_CLIENT_REACTIVE)
                .addParameter("telemetry", REDIS_TELEMETRY)
                .addParameter("keyMapper", keyMapperType)
                .addParameter("valueMapper", valueMapperType)
                .build()
        } else {
            throw UnsupportedOperationException("Unknown implementation: $cacheContract")
        }
    }

    private fun getCacheSuperConstructorCall(cacheContract: KSClassDeclaration, cacheType: KSTypeReference): CodeBlock {
        val configPath = cacheContract.annotations
            .filter { a -> a.annotationType.resolve().toClassName() == ANNOTATION_CACHE }
            .flatMap { a -> a.arguments }
            .filter { arg -> arg.name!!.getShortName() == "value" }
            .map { arg -> arg.value as String }
            .first()

        val resolved = cacheType.resolve()
        return if (resolved.toClassName() == CAFFEINE_CACHE) {
            CodeBlock.of("%S, config, factory, telemetry", configPath)
        } else if (resolved.toClassName() == REDIS_CACHE) {
            CodeBlock.of("%S, config, syncClient, reactiveClient, telemetry, keyMapper, valueMapper", configPath)
        } else {
            throw UnsupportedOperationException("Unknown implementation: $cacheContract")
        }
    }

    private fun getPackage(element: KSAnnotated): String {
        return element.toString()
    }
}

