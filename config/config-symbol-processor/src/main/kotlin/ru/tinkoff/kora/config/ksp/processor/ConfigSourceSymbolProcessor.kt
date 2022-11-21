package ru.tinkoff.kora.config.ksp.processor

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.addOriginatingKSFile
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.writeTo
import com.typesafe.config.Config
import ru.tinkoff.kora.common.Module
import ru.tinkoff.kora.config.common.ConfigSource
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor
import ru.tinkoff.kora.ksp.common.BaseSymbolProcessor
import ru.tinkoff.kora.ksp.common.visitClass
import java.io.IOException
import javax.annotation.processing.Generated

class ConfigSourceSymbolProcessor(
    environment: SymbolProcessorEnvironment
) : BaseSymbolProcessor(environment) {
    private val codeGenerator: CodeGenerator = environment.codeGenerator

    @OptIn(KspExperimental::class)
    override fun processRound(resolver: Resolver): List<KSAnnotated> {
        val classesToProcess = resolver.getSymbolsWithAnnotation(ConfigSource::class.qualifiedName!!).toList()

        classesToProcess.forEach {
            it.visitClass { config ->
                val typeBuilder = TypeSpec.interfaceBuilder(config.simpleName.asString() + "Module")
                val path = config.getAnnotationsByType(ConfigSource::class).first().value
                val name = StringBuilder(config.simpleName.asString())
                var parent = config.parentDeclaration
                while (parent is KSClassDeclaration && (parent.classKind == ClassKind.CLASS || parent.classKind == ClassKind.INTERFACE)) {
                    name.insert(0, parent.simpleName.asString())
                    parent = parent.parentDeclaration
                }
                name.replace(0, 1, name[0].lowercaseChar().toString())
                val function = FunSpec.builder(name.toString())
                    .returns(config.toClassName())
                    .addModifiers(KModifier.PUBLIC)
                    .addParameter("config", Config::class.java.asTypeName())
                    .addParameter(
                        "extractor",
                        ConfigValueExtractor::class.asClassName().parameterizedBy(config.toClassName())
                    )
                    .addStatement("val configValue = config.getValue(%S)", path)
                    .addStatement("return extractor.extract(configValue)")
                val type = typeBuilder.addFunction(function.build())
                    .addAnnotation(Module::class)
                    .addAnnotation(
                        AnnotationSpec.builder(Generated::class)
                            .addMember(
                                CodeBlock.of("%S", ConfigSourceSymbolProcessor::class.qualifiedName!!)
                            )
                            .build()
                    )
                    .addModifiers(KModifier.PUBLIC)
                    .addOriginatingKSFile(config.containingFile!!)
                    .build()
                val packageElement = config.packageName.asString()
                val fileSpec = FileSpec.builder(packageElement, type.name!!)
                    .addType(type)
                    .build()
                try {
                    fileSpec.writeTo(codeGenerator, false)
                } catch (e: IOException) {
                    throw RuntimeException(e)
                }
            }
        }
        return listOf()
    }
}

class ConfigSourceSymbolProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return ConfigSourceSymbolProcessor(environment)
    }
}
