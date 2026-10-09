package io.koraframework.http.client.symbol.processor

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import io.koraframework.ksp.common.AnnotationUtils.findAnnotation
import io.koraframework.ksp.common.AnnotationUtils.findValue
import io.koraframework.ksp.common.CommonClassNames
import io.koraframework.ksp.common.nestedIntoInterface
import io.koraframework.ksp.common.KspCommonUtils.addOriginatingKSFile
import io.koraframework.ksp.common.KspCommonUtils.generated

class ConfigModuleGenerator(val resolver: Resolver) {

    /**
     * Client and config are written as nested types of the module, the same way java annotation processor does it
     */
    fun generate(declaration: KSClassDeclaration, client: TypeSpec, config: TypeSpec): FileSpec {
        val lowercaseName = StringBuilder(declaration.simpleName.asString())
        lowercaseName.setCharAt(0, lowercaseName[0].lowercaseChar())
        val packageName = declaration.packageName.asString()
        var configPath = declaration.findAnnotation(ClassName("io.koraframework.http.client.common.annotation", "HttpClient"))
            ?.findValue<String>("value")!!
        if (configPath.isBlank()) {
            configPath = "httpClient.$lowercaseName"
        }
        val moduleName = declaration.moduleName()
        val configClass = declaration.configClassName()
        val extractorClass = CommonClassNames.configValueMapper.parameterizedBy(configClass)
        val type = TypeSpec.interfaceBuilder(moduleName)
            .generated(ConfigModuleGenerator::class)
            .addAnnotation(AnnotationSpec.builder(CommonClassNames.module).build())
            .addOriginatingKSFile(declaration)
            .addFunction(
                FunSpec.builder(lowercaseName.toString() + "_Config")
                    .returns(configClass)
                    .addParameter(ParameterSpec.builder("config", CommonClassNames.config).build())
                    .addParameter(ParameterSpec.builder("mapper", extractorClass).build())
                    .addStatement("return mapper.mapOrThrow(config.get(%S))", configPath)
                    .build()
            )
        return FileSpec.builder(packageName, moduleName)
            .addType(type.addType(client.nestedIntoInterface()).addType(config).build())
            .build()
    }
}
