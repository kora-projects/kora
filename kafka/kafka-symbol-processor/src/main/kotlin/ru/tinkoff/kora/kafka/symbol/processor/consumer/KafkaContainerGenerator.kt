package ru.tinkoff.kora.kafka.symbol.processor.consumer

import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import jakarta.annotation.Nullable
import ru.tinkoff.kora.kafka.symbol.processor.KafkaClassNames
import ru.tinkoff.kora.kafka.symbol.processor.KafkaUtils.containerFunName
import ru.tinkoff.kora.kafka.symbol.processor.KafkaUtils.getConsumerTags
import ru.tinkoff.kora.kafka.symbol.processor.consumer.KafkaHandlerGenerator.HandlerFunction
import ru.tinkoff.kora.ksp.common.AnnotationUtils.findValueNoDefault
import ru.tinkoff.kora.ksp.common.CommonClassNames
import ru.tinkoff.kora.ksp.common.KotlinPoetUtils.controlFlow
import ru.tinkoff.kora.ksp.common.TagUtils.addTag
import ru.tinkoff.kora.ksp.common.TagUtils.toTagSpecTypes

class KafkaContainerGenerator {
    fun generate(functionDeclaration: KSFunctionDeclaration, listenerAnnotation: KSAnnotation, handler: HandlerFunction, parameters: List<ConsumerParameter>): FunSpec {
        val keyType = handler.keyType
        val valueType = handler.valueType
        val handlerType = handler.funSpec.returnType as ParameterizedTypeName

        val consumerParameter = parameters.firstOrNull { it is ConsumerParameter.Consumer } as ConsumerParameter.Consumer?
        val tagAnnotation = functionDeclaration.getConsumerTags().toTagSpecTypes()

        val funBuilder = FunSpec.builder(functionDeclaration.containerFunName())
            .addParameter(ParameterSpec.builder("config", KafkaClassNames.kafkaConsumerConfig).addAnnotation(tagAnnotation).build())
            .addParameter(ParameterSpec.builder("handler", CommonClassNames.valueOf.parameterizedBy(handlerType)).addAnnotation(tagAnnotation).build())
            .addParameter(ParameterSpec.builder("keyDeserializer", KafkaClassNames.deserializer.parameterizedBy(keyType)).addTag(handler.keyTag).build())
            .addParameter(ParameterSpec.builder("valueDeserializer", KafkaClassNames.deserializer.parameterizedBy(valueType)).addTag(handler.valueTag).build())
            .addParameter("telemetryFactory", KafkaClassNames.kafkaConsumerTelemetryFactory.parameterizedBy(keyType, valueType))
            .addParameter(ParameterSpec.builder("rebalanceListener", KafkaClassNames.consumerRebalanceListener.copy(true))
                .addAnnotations(listOf(tagAnnotation))
                .build())
            .addAnnotation(CommonClassNames.root)
            .addAnnotation(tagAnnotation)
            .returns(CommonClassNames.lifecycle)

        val configPath = listenerAnnotation.findValueNoDefault<String>("value")!!
        val consumerName = configPath
            .replace('.', '-')
            .replace('_', '-')
        funBuilder.addStatement("val telemetry = telemetryFactory.get(%S, config.driverProperties(), config.telemetry())", consumerName)
        if (handlerType.rawType == KafkaClassNames.recordHandler) {
            funBuilder.addStatement("val wrappedHandler = %T.wrapHandlerRecord(telemetry, %L, handler)", KafkaClassNames.handlerWrapper, consumerParameter == null)
        } else {
            funBuilder.addStatement("val wrappedHandler = %T.wrapHandlerRecords(telemetry, %L, handler, config.allowEmptyRecords())", KafkaClassNames.handlerWrapper, consumerParameter == null)
        }
        funBuilder.controlFlow("if (config.driverProperties().getProperty(%T.GROUP_ID_CONFIG) == null)", KafkaClassNames.commonClientConfigs) {
            addStatement("val topics = config.topics()")
            addStatement("require(topics != null)")
            addStatement("require(topics.size == 1)")
            addStatement("return %T(config, topics[0], keyDeserializer, valueDeserializer, telemetry, wrappedHandler)", KafkaClassNames.kafkaAssignConsumerContainer)
            nextControlFlow("else")
            addStatement("return %T(config, keyDeserializer, valueDeserializer, wrappedHandler, rebalanceListener)", KafkaClassNames.kafkaSubscribeConsumerContainer)
        }
        return funBuilder.build()
    }
}
