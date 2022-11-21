package ru.tinkoff.kora.kafka.symbol.processor

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toClassName
import com.typesafe.config.Config
import ru.tinkoff.kora.common.Tag
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor
import ru.tinkoff.kora.kafka.common.annotation.KafkaIncoming
import ru.tinkoff.kora.kafka.common.config.KafkaConsumerConfig

@KspExperimental
class KafkaConfigGenerator(resolver: Resolver) {
    private val configDeclaration = resolver.getClassDeclarationByName(KafkaConsumerConfig::class.qualifiedName!!)!!

    fun generate(functionDeclaration: KSFunctionDeclaration): KafkaConfigData? {
        val controller = functionDeclaration.parentDeclaration as KSClassDeclaration
        val methodName = prepareMethodName(controller.simpleName.asString(), functionDeclaration.simpleName.asString())
        val listenerAnnotation = functionDeclaration.getAnnotationsByType(KafkaIncoming::class).firstOrNull() ?: return null
        val tagName = prepareTagName(controller.simpleName.asString(), functionDeclaration.simpleName.asString())
        val tagBuilder = TypeSpec.classBuilder(tagName)
        val configPath: String = listenerAnnotation.value
        val tagsBlock = CodeBlock.builder()
        tagsBlock.add("%L::class", tagName)
        val funBuilder = FunSpec.builder(methodName + "Config")
            .returns(configDeclaration.toClassName())
            .addParameter("config", Config::class)
            .addParameter(
                "extractor",
                ConfigValueExtractor::class.asClassName().parameterizedBy(configDeclaration.toClassName())
            )
            .addStatement("val configValue = config.getValue(%S)", configPath)
            .addStatement("return extractor.extract(configValue)")
            .addAnnotation(AnnotationSpec.builder(Tag::class).addMember(tagsBlock.build()).build())
        return KafkaConfigData(tagBuilder.build(), funBuilder.build())
    }
    data class KafkaConfigData(val tag: TypeSpec, val configFunction: FunSpec)

}
