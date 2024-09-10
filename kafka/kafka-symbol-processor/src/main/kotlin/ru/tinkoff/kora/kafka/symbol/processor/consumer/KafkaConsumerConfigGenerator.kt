package ru.tinkoff.kora.kafka.symbol.processor.consumer

import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeSpec
import ru.tinkoff.kora.kafka.symbol.processor.KafkaClassNames.kafkaConsumerConfig
import ru.tinkoff.kora.kafka.symbol.processor.KafkaUtils.configFunName
import ru.tinkoff.kora.kafka.symbol.processor.KafkaUtils.findConsumerUserTags
import ru.tinkoff.kora.kafka.symbol.processor.KafkaUtils.tagTypeName
import ru.tinkoff.kora.ksp.common.AnnotationUtils.findValueNoDefault
import ru.tinkoff.kora.ksp.common.CommonClassNames
import ru.tinkoff.kora.ksp.common.KspCommonUtils.generated
import ru.tinkoff.kora.ksp.common.TagUtils.toTagAnnotation
import ru.tinkoff.kora.ksp.common.TagUtils.toTagSpecTypes

class KafkaConsumerConfigGenerator {

    fun generate(functionDeclaration: KSFunctionDeclaration, listenerAnnotation: KSAnnotation): KafkaConfigData {
        var targetTags = functionDeclaration.findConsumerUserTags()
        val tagBuilder: TypeSpec?
        if(targetTags == null) {
            val tagName = functionDeclaration.tagTypeName()
            targetTags = listOf(ClassName(functionDeclaration.packageName.asString(), tagName))
            tagBuilder = TypeSpec.classBuilder(tagName)
                .generated(KafkaConsumerConfigGenerator::class)
                .build()
        } else {
            tagBuilder = null
        }

        val configPath = listenerAnnotation.findValueNoDefault<String>("value")!!
        val funBuilder = FunSpec.builder(functionDeclaration.configFunName())
            .returns(kafkaConsumerConfig)
            .addParameter("config", CommonClassNames.config)
            .addParameter(
                "extractor",
                CommonClassNames.configValueExtractor.parameterizedBy(kafkaConsumerConfig)
            )
            .addStatement("val configValue = config.get(%S)", configPath)
            .addStatement("return extractor.extract(configValue)!!")
            .addAnnotation(targetTags.toTagSpecTypes())
            .build()
        return KafkaConfigData(tagBuilder, funBuilder)
    }

    data class KafkaConfigData(val tag: TypeSpec?, val configFunction: FunSpec)
}
