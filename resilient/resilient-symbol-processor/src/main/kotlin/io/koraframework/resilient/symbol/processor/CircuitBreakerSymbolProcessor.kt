package io.koraframework.resilient.symbol.processor

import com.google.devtools.ksp.getAllSuperTypes
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.writeTo
import io.koraframework.ksp.common.AnnotationUtils.findAnnotation
import io.koraframework.ksp.common.AnnotationUtils.findValueNoDefault
import io.koraframework.ksp.common.BaseSymbolProcessor
import io.koraframework.ksp.common.CommonClassNames
import io.koraframework.ksp.common.CommonClassNames.configValueMapper
import io.koraframework.ksp.common.KspCommonUtils.addOriginatingKSFile
import io.koraframework.ksp.common.KspCommonUtils.generated
import io.koraframework.ksp.common.TagUtils.toTagAnnotation
import io.koraframework.ksp.common.exception.ProcessingErrorException
import io.koraframework.ksp.common.generatedClass
import io.koraframework.ksp.common.getOuterClassesAsPrefix

class CircuitBreakerSymbolProcessor(
    private val environment: SymbolProcessorEnvironment
) : BaseSymbolProcessor(environment) {

    override fun processRound(resolver: Resolver): List<KSAnnotated> {
        val deferred = ArrayList<KSAnnotated>()
        for (spec in SPECS) {
            val symbols = resolver.getSymbolsWithAnnotation(spec.annotation.canonicalName).toList()
            val symbolsToProcess = symbols.filter { it.validate() }.filterIsInstance<KSClassDeclaration>()
            for (resilientType in symbolsToProcess) {
                validate(resilientType, spec)
                val configPath = resilientType.findAnnotation(spec.annotation)
                    ?.findValueNoDefault<String>("value")
                    ?: continue
                if (configPath.isBlank()) {
                    throw ProcessingErrorException("@${spec.annotation.simpleName} config path can't be blank", resilientType)
                }

                val impl = implementationName(resilientType)
                FileSpec.builder(impl.packageName, impl.simpleName)
                    .addType(implementationSpec(resilientType, spec, impl, configPath))
                    .build()
                    .writeTo(codeGenerator = environment.codeGenerator, aggregating = false)

                val module = moduleName(resilientType)
                FileSpec.builder(module.packageName, module.simpleName)
                    .addType(moduleSpec(resilientType, spec, impl, module, configPath))
                    .build()
                    .writeTo(codeGenerator = environment.codeGenerator, aggregating = false)
            }
            deferred.addAll(symbols.filterNot { it.validate() })
        }

        return deferred
    }

    private fun validate(type: KSClassDeclaration, spec: Spec) {
        if (type.classKind != ClassKind.INTERFACE) {
            throw ProcessingErrorException(
                "@${spec.annotation.simpleName} is intended to be used on interfaces, but was: ${type.classKind}",
                type
            )
        }
        if (type.getAllSuperTypes().none { it.declaration.qualifiedName?.asString() == spec.contract.canonicalName }) {
            throw ProcessingErrorException(
                "@${spec.annotation.simpleName} annotated interface must extend ${spec.contract.canonicalName}",
                type
            )
        }
    }

    private fun implementationSpec(resilientType: KSClassDeclaration, spec: Spec, impl: ClassName, configPath: String): TypeSpec {
        val constructor = FunSpec.constructorBuilder()
            .addParameter("config", spec.config)
            .addParameter("telemetryFactory", spec.telemetryFactory)
            .addParameter("telemetryConfig", spec.telemetryConfig)
        if (spec.predicate != null) {
            constructor.addParameter("failurePredicate", spec.predicate.copy(nullable = true))
        }

        val type = TypeSpec.classBuilder(impl)
            .generated(CircuitBreakerSymbolProcessor::class)
            .addOriginatingKSFile(resilientType)
            .addModifiers(KModifier.PUBLIC)
            .superclass(spec.baseImplementation)
            .addSuperinterface(resilientType.toClassName())
            .primaryConstructor(constructor.build())

        val simpleName = resilientType.simpleName.asString()
        when (spec.contract) {
            RETRY -> type.addSuperclassConstructorParameter("%S", simpleName)
                .addSuperclassConstructorParameter("config")
                .addSuperclassConstructorParameter("failurePredicate")
                .addSuperclassConstructorParameter("retryBudget(config)")
                .addSuperclassConstructorParameter("telemetryFactory.get(CONFIG_PATH, telemetryConfig)")
            CIRCUIT_BREAKER -> type.addSuperclassConstructorParameter("%S", simpleName)
                .addSuperclassConstructorParameter("config")
                .addSuperclassConstructorParameter("failurePredicate")
                .addSuperclassConstructorParameter("telemetryFactory.get(CONFIG_PATH, telemetryConfig)")
            TIMEOUTER -> type.addSuperclassConstructorParameter("%S", simpleName)
                .addSuperclassConstructorParameter("config.duration()")
                .addSuperclassConstructorParameter("telemetryFactory.get(CONFIG_PATH, telemetryConfig)")
                .addSuperclassConstructorParameter("config")
            else -> type.addSuperclassConstructorParameter("%S", simpleName)
                .addSuperclassConstructorParameter("config")
                .addSuperclassConstructorParameter("telemetryFactory.get(CONFIG_PATH, telemetryConfig)")
        }

        val companion = TypeSpec.companionObjectBuilder()
            .addProperty(
                PropertySpec.builder("CONFIG_PATH", String::class, KModifier.PRIVATE, KModifier.CONST)
                    .initializer("%S", configPath)
                    .build()
            )
        if (spec.contract == RETRY) {
            companion.addFunction(
                FunSpec.builder("retryBudget")
                    .addModifiers(KModifier.PRIVATE)
                    .addParameter("config", spec.config)
                    .returns(KORA_RETRY_BUDGET.copy(nullable = true))
                    .addCode(
                        """
                        val retryBudget = config.retryBudget()
                        if (retryBudget == null || !retryBudget.enabled()) {
                            return null
                        }
                        return %T(
                            retryBudget.ratio(),
                            retryBudget.tokensMax(),
                            retryBudget.tokensInitial(),
                            retryBudget.minTokensPerSecond()
                        )
                        """.trimIndent(),
                        KORA_RETRY_BUDGET
                    )
                    .build()
            )
        }

        return type.addType(companion.build()).build()
    }

    private fun moduleSpec(
        resilientType: KSClassDeclaration,
        spec: Spec,
        impl: ClassName,
        module: ClassName,
        configPath: String
    ): TypeSpec {
        val contract = resilientType.toClassName()
        val methodPrefix = (resilientType.getOuterClassesAsPrefix().substring(1) + resilientType.simpleName.asString())
            .replaceFirstChar { it.lowercaseChar() }
        val mapperType = configValueMapper.parameterizedBy(spec.config)

        val implMethod = FunSpec.builder("${methodPrefix}_Impl")
            .addModifiers(KModifier.PUBLIC)
            .addParameter(
                ParameterSpec.builder("config", spec.config)
                    .addAnnotation(contract.toTagAnnotation())
                    .build()
            )
            .addParameter("telemetryFactory", spec.telemetryFactory)
            .addParameter("resilientConfig", RESILIENT_CONFIG)
            .returns(contract)
        implMethod.addStatement("val telemetryConfig = %T(resilientConfig.%L(), config.telemetry())", spec.operationTelemetryConfig, spec.telemetryAccessor)
        if (spec.predicate != null) {
            implMethod.addParameter(
                ParameterSpec.builder("failurePredicate", spec.predicate.copy(nullable = true))
                    .addAnnotation(contract.toTagAnnotation())
                    .build()
            )
            implMethod.addStatement("return %T(config, telemetryFactory, telemetryConfig, failurePredicate)", impl)
        } else {
            implMethod.addStatement("return %T(config, telemetryFactory, telemetryConfig)", impl)
        }

        return TypeSpec.interfaceBuilder(module)
            .generated(CircuitBreakerSymbolProcessor::class)
            .addOriginatingKSFile(resilientType)
            .addAnnotation(CommonClassNames.module)
            .addFunction(
                FunSpec.builder("${methodPrefix}_Config")
                    .addModifiers(KModifier.PUBLIC)
                    .addAnnotation(contract.toTagAnnotation())
                    .addParameter("config", CommonClassNames.config)
                    .addParameter("mapper", mapperType)
                    .returns(spec.config)
                    .addStatement("return mapper.mapOrThrow(config.get(%S))!!", configPath)
                    .build()
            )
            .addFunction(implMethod.build())
            .build()
    }

    private fun implementationName(resilientType: KSClassDeclaration): ClassName {
        val contract = resilientType.toClassName()
        return ClassName(contract.packageName, resilientType.generatedClass("Impl"))
    }

    private fun moduleName(resilientType: KSClassDeclaration): ClassName {
        val contract = resilientType.toClassName()
        return ClassName(contract.packageName, resilientType.generatedClass("Module"))
    }

    private data class Spec(
        val annotation: ClassName,
        val contract: ClassName,
        val baseImplementation: ClassName,
        val config: ClassName,
        val predicate: ClassName?,
        val telemetryFactory: ClassName,
        val telemetryConfig: ClassName,
        val operationTelemetryConfig: ClassName,
        val telemetryAccessor: String
    )

    private companion object {
        private val CIRCUIT_BREAKER = ClassName("io.koraframework.resilient.circuitbreaker", "CircuitBreaker")
        private val RETRY = ClassName("io.koraframework.resilient.retry", "Retry")
        private val TIMEOUTER = ClassName("io.koraframework.resilient.timeout", "Timeouter")
        private val KORA_RETRY_BUDGET = ClassName("io.koraframework.resilient.retry", "KoraRetryBudget")
        private val RESILIENT_CONFIG = ClassName("io.koraframework.resilient", "ResilientConfig")

        private val SPECS = listOf(
            Spec(
                ClassName("io.koraframework.resilient.circuitbreaker.annotation", "CircuitBreakerSpec"),
                CIRCUIT_BREAKER,
                ClassName("io.koraframework.resilient.circuitbreaker", "KoraCircuitBreaker"),
                ClassName("io.koraframework.resilient.circuitbreaker", "CircuitBreakerConfig"),
                ClassName("io.koraframework.resilient.circuitbreaker", "CircuitBreakerPredicate"),
                ClassName("io.koraframework.resilient.circuitbreaker.telemetry", "CircuitBreakerTelemetryFactory"),
                ClassName("io.koraframework.resilient.circuitbreaker.telemetry", "CircuitBreakerTelemetryConfig"),
                ClassName("io.koraframework.resilient.circuitbreaker.telemetry", "CircuitBreakerOperationTelemetryConfig"),
                "circuitBreaker"
            ),
            Spec(
                ClassName("io.koraframework.resilient.retry.annotation", "RetrySpec"),
                RETRY,
                ClassName("io.koraframework.resilient.retry", "KoraRetry"),
                ClassName("io.koraframework.resilient.retry", "RetryConfig"),
                ClassName("io.koraframework.resilient.retry", "RetryPredicate"),
                ClassName("io.koraframework.resilient.retry.telemetry", "RetryTelemetryFactory"),
                ClassName("io.koraframework.resilient.retry.telemetry", "RetryTelemetryConfig"),
                ClassName("io.koraframework.resilient.retry.telemetry", "RetryOperationTelemetryConfig"),
                "retry"
            ),
            Spec(
                ClassName("io.koraframework.resilient.timeout.annotation", "TimeoutSpec"),
                TIMEOUTER,
                ClassName("io.koraframework.resilient.timeout", "KoraTimeouter"),
                ClassName("io.koraframework.resilient.timeout", "TimeoutConfig"),
                null,
                ClassName("io.koraframework.resilient.timeout.telemetry", "TimeoutTelemetryFactory"),
                ClassName("io.koraframework.resilient.timeout.telemetry", "TimeoutTelemetryConfig"),
                ClassName("io.koraframework.resilient.timeout.telemetry", "TimeoutOperationTelemetryConfig"),
                "timeout"
            ),
            Spec(
                ClassName("io.koraframework.resilient.ratelimiter.annotation", "RateLimiterSpec"),
                ClassName("io.koraframework.resilient.ratelimiter", "RateLimiter"),
                ClassName("io.koraframework.resilient.ratelimiter", "KoraRateLimiter"),
                ClassName("io.koraframework.resilient.ratelimiter", "RateLimiterConfig"),
                null,
                ClassName("io.koraframework.resilient.ratelimiter.telemetry", "RateLimiterTelemetryFactory"),
                ClassName("io.koraframework.resilient.ratelimiter.telemetry", "RateLimiterTelemetryConfig"),
                ClassName("io.koraframework.resilient.ratelimiter.telemetry", "RateLimiterOperationTelemetryConfig"),
                "rateLimiter"
            )
        )
    }
}
