package io.koraframework.kafka.symbol.processor.consumer

import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeSpec
import io.koraframework.kafka.symbol.processor.KafkaClassNames.kafkaConsumerConfig
import io.koraframework.kafka.symbol.processor.KafkaUtils.configFunName
import io.koraframework.kafka.symbol.processor.KafkaUtils.findConsumerUserTag
import io.koraframework.kafka.symbol.processor.KafkaUtils.tagType
import io.koraframework.ksp.common.AnnotationUtils.findValueNoDefault
import io.koraframework.ksp.common.CommonClassNames
import io.koraframework.ksp.common.KspCommonUtils.generated
import io.koraframework.ksp.common.TagUtils.addTag

class KafkaConsumerConfigGenerator {

    fun generate(functionDeclaration: KSFunctionDeclaration, listenerAnnotation: KSAnnotation): KafkaConfigData {
        var targetTags = functionDeclaration.findConsumerUserTag()
        val tagBuilder: TypeSpec?
        if(targetTags == null) {
            val tag = functionDeclaration.tagType()
            targetTags = tag
            tagBuilder = TypeSpec.classBuilder(tag.simpleName)
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
                "mapper",
                CommonClassNames.configValueMapper.parameterizedBy(kafkaConsumerConfig)
            )
            .addStatement("return mapper.mapOrThrow(config.get(%S))!!", configPath)
            .addTag(targetTags)
            .build()
        return KafkaConfigData(tagBuilder, funBuilder)
    }

    data class KafkaConfigData(val tag: TypeSpec?, val configFunction: FunSpec)
}
