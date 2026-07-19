package io.koraframework.resilient.symbol.processor

import com.google.devtools.ksp.getAllSuperTypes
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
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
        val symbols = resolver.getSymbolsWithAnnotation(ANNOTATION_TYPE.canonicalName).toList()
        val symbolsToProcess = symbols.filter { it.validate() }.filterIsInstance<KSClassDeclaration>()
        for (circuitBreaker in symbolsToProcess) {
            validate(circuitBreaker)
            val configPath = circuitBreaker.findAnnotation(ANNOTATION_TYPE)
                ?.findValueNoDefault<String>("value")
                ?: continue
            validate(circuitBreaker, configPath)

            val impl = implementationName(circuitBreaker)
            FileSpec.builder(impl.packageName, impl.simpleName)
                .addType(implementationSpec(circuitBreaker, impl, configPath))
                .build()
                .writeTo(codeGenerator = environment.codeGenerator, aggregating = false)

            val module = moduleName(circuitBreaker)
            FileSpec.builder(module.packageName, module.simpleName)
                .addType(moduleSpec(circuitBreaker, impl, module, configPath))
                .build()
                .writeTo(codeGenerator = environment.codeGenerator, aggregating = false)
        }

        return symbols.filterNot { it.validate() }.toList()
    }

    private fun validate(type: KSClassDeclaration) {
        if (type.classKind != ClassKind.INTERFACE) {
            throw ProcessingErrorException(
                "@${ANNOTATION_TYPE.simpleName} is intended to be used on interfaces, but was: ${type.classKind}",
                type
            )
        }
        if (type.getAllSuperTypes().none { it.declaration.qualifiedName?.asString() == CIRCUIT_BREAKER.canonicalName }) {
            throw ProcessingErrorException(
                "@${ANNOTATION_TYPE.simpleName} annotated interface must extend ${CIRCUIT_BREAKER.canonicalName}",
                type
            )
        }
    }

    private fun validate(type: KSClassDeclaration, configPath: String) {
        if (configPath.isBlank()) {
            throw ProcessingErrorException("@${ANNOTATION_TYPE.simpleName} config path can't be blank", type)
        }
    }

    private fun implementationSpec(circuitBreaker: KSClassDeclaration, impl: ClassName, configPath: String): TypeSpec {
        return TypeSpec.classBuilder(impl)
            .generated(CircuitBreakerSymbolProcessor::class)
            .addOriginatingKSFile(circuitBreaker)
            .addModifiers(KModifier.PUBLIC)
            .superclass(KORA_CIRCUIT_BREAKER)
            .addSuperinterface(circuitBreaker.toClassName())
            .primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameter("name", String::class)
                    .addParameter("config", CIRCUIT_BREAKER_CONFIG)
                    .addParameter("failurePredicate", CIRCUIT_BREAKER_PREDICATE.copy(nullable = true))
                    .addParameter("telemetryFactory", CIRCUIT_BREAKER_TELEMETRY_FACTORY)
                    .build()
            )
            .addSuperclassConstructorParameter("name")
            .addSuperclassConstructorParameter("config")
            .addSuperclassConstructorParameter("failurePredicate")
            .addSuperclassConstructorParameter("telemetryFactory.get(CONFIG_PATH, config.telemetry())")
            .addType(
                TypeSpec.companionObjectBuilder()
                    .addProperty(
                        PropertySpec.builder("CONFIG_PATH", String::class, KModifier.PRIVATE, KModifier.CONST)
                            .initializer("%S", configPath)
                            .build()
                    )
                    .build()
            )
            .build()
    }

    private fun moduleSpec(
        circuitBreaker: KSClassDeclaration,
        impl: ClassName,
        module: ClassName,
        configPath: String
    ): TypeSpec {
        val contract = circuitBreaker.toClassName()
        val methodPrefix = (circuitBreaker.getOuterClassesAsPrefix().substring(1) + circuitBreaker.simpleName.asString())
            .replaceFirstChar { it.lowercaseChar() }
        val mapperType = configValueMapper.parameterizedBy(CIRCUIT_BREAKER_CONFIG)

        return TypeSpec.interfaceBuilder(module)
            .generated(CircuitBreakerSymbolProcessor::class)
            .addOriginatingKSFile(circuitBreaker)
            .addAnnotation(CommonClassNames.module)
            .addFunction(
                FunSpec.builder("${methodPrefix}_Config")
                    .addModifiers(KModifier.PUBLIC)
                    .addAnnotation(contract.toTagAnnotation())
                    .addParameter("config", CommonClassNames.config)
                    .addParameter("mapper", mapperType)
                    .returns(CIRCUIT_BREAKER_CONFIG)
                    .addStatement("return mapper.mapOrThrow(config.get(%S))!!", configPath)
                    .build()
            )
            .addFunction(
                FunSpec.builder("${methodPrefix}_Impl")
                    .addModifiers(KModifier.PUBLIC)
                    .addParameter(
                        ParameterSpec.builder("config", CIRCUIT_BREAKER_CONFIG)
                            .addAnnotation(contract.toTagAnnotation())
                            .build()
                    )
                    .addParameter(
                        ParameterSpec.builder("failurePredicate", CIRCUIT_BREAKER_PREDICATE.copy(nullable = true))
                            .addAnnotation(contract.toTagAnnotation())
                            .build()
                    )
                    .addParameter("telemetryFactory", CIRCUIT_BREAKER_TELEMETRY_FACTORY)
                    .returns(contract)
                    .addStatement("return %T(%S, config, failurePredicate, telemetryFactory)", impl, circuitBreaker.simpleName.asString())
                    .build()
            )
            .build()
    }

    private fun implementationName(circuitBreaker: KSClassDeclaration): ClassName {
        val contract = circuitBreaker.toClassName()
        return ClassName(contract.packageName, circuitBreaker.generatedClass("Impl"))
    }

    private fun moduleName(circuitBreaker: KSClassDeclaration): ClassName {
        val contract = circuitBreaker.toClassName()
        return ClassName(contract.packageName, circuitBreaker.generatedClass("Module"))
    }

    private companion object {
        private val ANNOTATION_TYPE = ClassName("io.koraframework.resilient.circuitbreaker.annotation", "CircuitBreaker")
        private val CIRCUIT_BREAKER = ClassName("io.koraframework.resilient.circuitbreaker", "CircuitBreaker")
        private val KORA_CIRCUIT_BREAKER = ClassName("io.koraframework.resilient.circuitbreaker", "KoraCircuitBreaker")
        private val CIRCUIT_BREAKER_CONFIG = ClassName("io.koraframework.resilient.circuitbreaker", "CircuitBreakerConfig")
        private val CIRCUIT_BREAKER_PREDICATE = ClassName("io.koraframework.resilient.circuitbreaker", "CircuitBreakerPredicate")
        private val CIRCUIT_BREAKER_TELEMETRY_FACTORY = ClassName("io.koraframework.resilient.circuitbreaker.telemetry", "CircuitBreakerTelemetryFactory")
    }
}
