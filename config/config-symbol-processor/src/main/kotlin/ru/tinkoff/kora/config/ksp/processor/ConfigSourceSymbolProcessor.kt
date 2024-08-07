package ru.tinkoff.kora.config.ksp.processor

import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.addOriginatingKSFile
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.writeTo
import ru.tinkoff.kora.config.ksp.ConfigClassNames
import ru.tinkoff.kora.ksp.common.AnnotationUtils.findAnnotation
import ru.tinkoff.kora.ksp.common.AnnotationUtils.findValue
import ru.tinkoff.kora.ksp.common.BaseSymbolProcessor
import ru.tinkoff.kora.ksp.common.CommonClassNames
import ru.tinkoff.kora.ksp.common.KspCommonUtils.generated
import ru.tinkoff.kora.ksp.common.visitClass
import java.io.IOException

class ConfigSourceSymbolProcessor(
    environment: SymbolProcessorEnvironment
) : BaseSymbolProcessor(environment) {
    private val codeGenerator: CodeGenerator = environment.codeGenerator

    override fun processRound(resolver: Resolver): List<KSAnnotated> {
        val classesToProcess = resolver.getSymbolsWithAnnotation(ConfigClassNames.configSourceAnnotation.canonicalName).toList()

        classesToProcess.forEach {
            it.visitClass { config ->
                val typeBuilder = TypeSpec.interfaceBuilder(config.simpleName.asString() + "Module")
                val configSource = config.findAnnotation(ConfigClassNames.configSourceAnnotation)!!
                val path = configSource.findValue<String>("value")!!
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
                    .addParameter("config", ConfigClassNames.config)
                    .addParameter(
                        "extractor",
                        ConfigClassNames.configValueExtractor.parameterizedBy(config.toClassName())
                    )
                    .addStatement("val configValue = config.get(%S)", path)
                    .addStatement("return extractor.extract(configValue) ?: throw %T.missingValueAfterParse(configValue)", CommonClassNames.configValueExtractionException)
                val type = typeBuilder.addFunction(function.build())
                    .addAnnotation(CommonClassNames.module)
                    .generated(ConfigSourceSymbolProcessor::class)
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
