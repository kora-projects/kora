package ru.tinkoff.kora.kafka.symbol.processor.consumer

import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeSpec
import ru.tinkoff.kora.kafka.symbol.processor.KafkaClassNames.kafkaConsumerConfig
import ru.tinkoff.kora.kafka.symbol.processor.KafkaUtils.configFunName
import ru.tinkoff.kora.kafka.symbol.processor.KafkaUtils.tagTypeName
import ru.tinkoff.kora.ksp.common.AnnotationUtils.findValueNoDefault
import ru.tinkoff.kora.ksp.common.CommonClassNames
import ru.tinkoff.kora.ksp.common.TagUtils.toTagAnnotation

class KafkaConsumerConfigGenerator {

    fun generate(functionDeclaration: KSFunctionDeclaration, listenerAnnotation: KSAnnotation): KafkaConfigData {
        val tagName = functionDeclaration.tagTypeName()
        val tagBuilder = TypeSpec.classBuilder(tagName).build()
        val configPath = listenerAnnotation.findValueNoDefault<String>("value")!!

        val funBuilder = FunSpec.builder(functionDeclaration.configFunName())
            .returns(kafkaConsumerConfig)
            .addParameter("config", CommonClassNames.config)
            .addParameter(
                "extractor",
                CommonClassNames.configValueExtractor.parameterizedBy(kafkaConsumerConfig)
            )
            .addStatement("val configValue = config.getValue(%S)", configPath)
            .addStatement("return extractor.extract(configValue)")
            .addAnnotation(listOf(tagName).toTagAnnotation())
            .build()
        return KafkaConfigData(tagBuilder, funBuilder)
    }

    data class KafkaConfigData(val tag: TypeSpec, val configFunction: FunSpec)
}
