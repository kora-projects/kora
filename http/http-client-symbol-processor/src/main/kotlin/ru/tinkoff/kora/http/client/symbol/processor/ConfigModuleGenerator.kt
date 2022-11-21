package ru.tinkoff.kora.http.client.symbol.processor

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.addOriginatingKSFile
import com.typesafe.config.Config
import ru.tinkoff.kora.common.Module
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor
import ru.tinkoff.kora.http.client.common.annotation.HttpClient
import javax.annotation.processing.Generated

@KspExperimental
class ConfigModuleGenerator(val resolver: Resolver) {

    fun generate(declaration: KSClassDeclaration): FileSpec {
        val lowercaseName = StringBuilder(declaration.simpleName.asString())
        lowercaseName.setCharAt(0, lowercaseName[0].lowercaseChar())
        val packageName = declaration.packageName.asString()
        var configPath: String = declaration.getAnnotationsByType(
            HttpClient::class
        ).first().configPath
        if (configPath.isBlank()) {
            configPath = "httpClient.$lowercaseName"
        }
        val configName = declaration.configName()
        val moduleName = declaration.moduleName()
        val configClass = ClassName(packageName, configName)
        val extractorClass = ConfigValueExtractor::class.asClassName().parameterizedBy(configClass)
        val type = TypeSpec.interfaceBuilder(moduleName)
            .addAnnotation(
                AnnotationSpec.builder(Generated::class)
                    .addMember("%S", ConfigModuleGenerator::class.qualifiedName!!).build()
            )
            .addAnnotation(AnnotationSpec.builder(Module::class).build())
            .addOriginatingKSFile(declaration.containingFile!!)
            .addFunction(
                FunSpec.builder(lowercaseName.toString() + "Config")
                    .returns(configClass)
                    .addParameter(ParameterSpec.builder("config", Config::class).build())
                    .addParameter(ParameterSpec.builder("extractor", extractorClass).build())
                    .addStatement("val value = config.getValue(%S)", configPath)
                    .addStatement("return extractor.extract(value)")
                    .build()
            )
        return FileSpec.builder(packageName, moduleName)
            .addType(type.build())
            .build()
    }
}
