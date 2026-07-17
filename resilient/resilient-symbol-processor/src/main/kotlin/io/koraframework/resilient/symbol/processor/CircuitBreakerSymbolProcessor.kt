package io.koraframework.resilient.symbol.processor

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.writeTo
import io.koraframework.ksp.common.AnnotationUtils.findAnnotation
import io.koraframework.ksp.common.AnnotationUtils.findValue
import io.koraframework.ksp.common.BaseSymbolProcessor
import io.koraframework.ksp.common.CommonClassNames
import io.koraframework.ksp.common.KspCommonUtils.generated
import io.koraframework.ksp.common.TagUtils.toTagAnnotation
import io.koraframework.ksp.common.exception.ProcessingErrorException

class CircuitBreakerSymbolProcessor(private val env: SymbolProcessorEnvironment) : BaseSymbolProcessor(env) {
    private val generatedConfigPathByTag = LinkedHashMap<String, String>()
    private val generatedByConfigPath = LinkedHashSet<String>()

    override fun processRound(resolver: Resolver): List<KSAnnotated> {
        val configPaths = LinkedHashMap<String, KSFunctionDeclaration>()
        val annotatedFunctions = resolver
            .getSymbolsWithAnnotation(ANNOTATION_TYPE.canonicalName)
            .filterIsInstance<KSFunctionDeclaration>()

        for (function in annotatedFunctions) {
            val annotation = function.findAnnotation(ANNOTATION_TYPE) ?: continue
            val configPath = annotation.findValue<String>("value") ?: continue
            validate(function, configPath)
            validateGeneratedNameCollision(function, configPath)
            configPaths.putIfAbsent(configPath, function)
        }

        for ((configPath, function) in configPaths) {
            if (!generatedByConfigPath.add(configPath)) {
                continue
            }
            val tag = CircuitBreakerTagUtils.tagName(configPath)
            val module = CircuitBreakerTagUtils.moduleName(configPath)
            if (resolver.getClassDeclarationByName(tag.canonicalName) == null) {
                generateTag(tag, configPath, function)
            }
            if (resolver.getClassDeclarationByName(module.canonicalName) == null) {
                generateModule(tag, module, configPath, function)
            }
        }
        return emptyList()
    }

    private fun validate(function: KSFunctionDeclaration, configPath: String) {
        if (configPath.isBlank()) {
            throw ProcessingErrorException("@${ANNOTATION_TYPE.simpleName} config path can't be blank", function)
        }
        if (CircuitBreakerTagUtils.isReservedPath(configPath)) {
            throw ProcessingErrorException("@${ANNOTATION_TYPE.simpleName} config path '$configPath' is reserved", function)
        }
    }

    private fun validateGeneratedNameCollision(function: KSFunctionDeclaration, configPath: String) {
        val tagName = CircuitBreakerTagUtils.tagName(configPath).canonicalName
        val previousPath = generatedConfigPathByTag.putIfAbsent(tagName, configPath)
        if (previousPath != null && previousPath != configPath) {
            throw ProcessingErrorException(
                "@${ANNOTATION_TYPE.simpleName} config paths '$previousPath' and '$configPath' generate the same tag '$tagName'; use paths with different alphanumeric names",
                function
            )
        }
    }

    private fun generateTag(tag: ClassName, configPath: String, function: KSFunctionDeclaration) {
        val type = TypeSpec.classBuilder(tag)
            .generated(CircuitBreakerSymbolProcessor::class)
            .primaryConstructor(FunSpec.constructorBuilder().addModifiers(KModifier.PRIVATE).build())
            .addType(
                TypeSpec.companionObjectBuilder()
                    .addProperty(
                        PropertySpec.builder(CircuitBreakerTagUtils.CONFIG_PATH_FIELD, String::class, KModifier.CONST)
                            .initializer("%S", configPath)
                            .build()
                    )
                    .build()
            )
            .build()

        FileSpec.get(tag.packageName, type)
            .writeTo(env.codeGenerator, Dependencies.ALL_FILES)
    }

    private fun generateModule(tag: ClassName, module: ClassName, configPath: String, function: KSFunctionDeclaration) {
        val type = TypeSpec.interfaceBuilder(module)
            .generated(CircuitBreakerSymbolProcessor::class)
            .addAnnotation(CommonClassNames.module)
            .addFunction(
                FunSpec.builder(CircuitBreakerTagUtils.factoryMethodName(configPath))
                    .addAnnotation(CommonClassNames.factoryModule)
                    .addAnnotation(tag.toTagAnnotation())
                    .returns(CIRCUIT_BREAKER_FACTORY_MODULE)
                    .addStatement("return %T(%T.%L)", CIRCUIT_BREAKER_FACTORY_MODULE, tag, CircuitBreakerTagUtils.CONFIG_PATH_FIELD)
                    .build()
            )
            .build()

        FileSpec.get(module.packageName, type)
            .writeTo(env.codeGenerator, Dependencies.ALL_FILES)
    }

    private companion object {
        private val ANNOTATION_TYPE = ClassName("io.koraframework.resilient.circuitbreaker.annotation", "CircuitBreaker")
        private val CIRCUIT_BREAKER_FACTORY_MODULE = ClassName("io.koraframework.resilient.circuitbreaker", "CircuitBreakerFactoryModule")
    }
}
