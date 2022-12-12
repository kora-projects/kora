package ru.tinkoff.kora.config.ksp

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.typesafe.config.Config
import ru.tinkoff.kora.common.Module
import ru.tinkoff.kora.common.Tag
import ru.tinkoff.kora.common.annotation.Generated
import ru.tinkoff.kora.config.common.ConfigRoot
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor
import ru.tinkoff.kora.config.ksp.exception.NewRoundWantedException
import ru.tinkoff.kora.ksp.common.parseTagValue

@KspExperimental
class ConfigRootModuleGenerator(
    val resolver: Resolver
) {
    private val configParserDeclaration = resolver.getClassDeclarationByName(ConfigValueExtractor::class.qualifiedName!!)!!

    fun generateModule(declaration: KSClassDeclaration): FileSpec {
        val packageName = declaration.packageName.asString()
        val typeName = declaration.simpleName.asString() + "Module"
        val typeBuilder = TypeSpec.interfaceBuilder(typeName)
            .addAnnotation(
                AnnotationSpec.builder(Generated::class)
                    .addMember(CodeBlock.of("%S", ConfigRootModuleGenerator::class.qualifiedName!!)).build()
            )
        val configRoot = declaration.getAnnotationsByType(ConfigRoot::class).firstOrNull()
        val i = configRoot?.value?.iterator()
        if (i?.hasNext() == true) typeBuilder.addAnnotation(
            AnnotationSpec.builder(Module::class)
                .build()
        )
        val parserType = configParserDeclaration.asType(listOf(resolver.getTypeArgument(resolver.createKSTypeReferenceFromKSType(declaration.asStarProjectedType()), Variance.INVARIANT)))
        val rootConfigTypeName = declaration.toClassName()
        val configFunction = FunSpec.builder(declaration.simpleName.asString().decapitalize())
            .returns(declaration.asStarProjectedType().toTypeName())
            .addParameter(ParameterSpec.builder("config", Config::class.java.asTypeName()).build())
            .addParameter(ParameterSpec.builder("configParser", parserType.toTypeName()).build())
            .addStatement("return configParser.extract(config.root())")
            .build()
        typeBuilder.addFunction(configFunction)
        for (field in collectFieldsAccessors(declaration)) {
            val returnType = field.property.type.resolve()
            if (returnType.isError) {
                throw NewRoundWantedException(declaration)
            }
            val methodBuilder = FunSpec.builder(field.name + "ConfigValue")
                .returns(returnType.toTypeName())
                .addParameter(ParameterSpec.builder("config", rootConfigTypeName).build())
                .addStatement("return config.%L", field.property.simpleName.asString())
            if (field.tags.isNotEmpty()) {
                val tagsBlock = CodeBlock.builder()
                for (i in field.tags.indices) {
                    val tag = field.tags[i]
                    val trailingComma = if (i + 1 == field.tags.size) "" else ", "
                    tagsBlock.add("%T::class%L", tag.toTypeName(), trailingComma)
                }
                methodBuilder.addAnnotation(
                    AnnotationSpec.builder(Tag::class.java).addMember(tagsBlock.build()).build()
                )
            }
            typeBuilder.addFunction(methodBuilder.build())
        }
        val type = typeBuilder.build()
        return FileSpec.builder(packageName, type.name!!).addType(type).build()
    }

    private fun collectFieldsAccessors(element: KSClassDeclaration): List<PropertyMeta> {
        return if (element.classKind == ClassKind.CLASS) {
            collectFields(element)
        } else listOf()
    }

    private fun collectFields(declaration: KSClassDeclaration): List<PropertyMeta> {
        val fields = declaration.getDeclaredProperties().toList()
        return collectFieldsMeta(fields)
    }

    private fun collectFieldsMeta(
        fields: List<KSPropertyDeclaration>,
    ): List<PropertyMeta> {
        return fields.map { field ->
            val tags = parseTagValue(field)
            PropertyMeta(field.simpleName.asString(), field, tags)
        }.toList()
    }

    data class PropertyMeta(val name: String, val property: KSPropertyDeclaration, val tags: List<KSType>)
}
