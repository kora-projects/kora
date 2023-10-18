package ru.tinkoff.kora.kafka.symbol.processor.consumer

import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import ru.tinkoff.kora.kafka.symbol.processor.KafkaClassNames
import ru.tinkoff.kora.kafka.symbol.processor.KafkaUtils.containerFunName
import ru.tinkoff.kora.kafka.symbol.processor.KafkaUtils.tagTypeName
import ru.tinkoff.kora.kafka.symbol.processor.consumer.KafkaHandlerGenerator.HandlerFunction
import ru.tinkoff.kora.ksp.common.CommonClassNames
import ru.tinkoff.kora.ksp.common.KotlinPoetUtils.controlFlow
import ru.tinkoff.kora.ksp.common.TagUtils.addTag
import ru.tinkoff.kora.ksp.common.TagUtils.toTagAnnotation

class KafkaContainerGenerator {
    fun generate(functionDeclaration: KSFunctionDeclaration, handler: HandlerFunction, parameters: List<ConsumerParameter>): FunSpec {
        val keyType = handler.keyType
        val valueType = handler.valueType
        val handlerType = handler.funSpec.returnType as ParameterizedTypeName

        val consumerParameter = parameters.firstOrNull { it is ConsumerParameter.Consumer } as ConsumerParameter.Consumer?
        val tagName = functionDeclaration.tagTypeName()
        val tagAnnotation = listOf(tagName).toTagAnnotation()

        val funBuilder = FunSpec.builder(functionDeclaration.containerFunName())
            .addParameter(ParameterSpec.builder("config", KafkaClassNames.kafkaConsumerConfig).addAnnotation(tagAnnotation).build())
            .addParameter(ParameterSpec.builder("handler", CommonClassNames.valueOf.parameterizedBy(handlerType)).addAnnotation(tagAnnotation).build())
            .addParameter(ParameterSpec.builder("keyDeserializer", KafkaClassNames.deserializer.parameterizedBy(keyType)).addTag(handler.keyTag).build())
            .addParameter(ParameterSpec.builder("valueDeserializer", KafkaClassNames.deserializer.parameterizedBy(valueType)).addTag(handler.valueTag).build())
            .addParameter("telemetryFactory", KafkaClassNames.kafkaConsumerTelemetryFactory.parameterizedBy(keyType, valueType))
            .addAnnotation(CommonClassNames.root)
            .addAnnotation(tagAnnotation)
            .returns(CommonClassNames.lifecycle)
        funBuilder.addStatement("val telemetry = telemetryFactory.get(config.telemetry())")
        if (handlerType.rawType == KafkaClassNames.recordHandler) {
            funBuilder.addStatement("val wrappedHandler = %T.wrapHandlerRecord(telemetry, %L, handler)", KafkaClassNames.handlerWrapper, consumerParameter == null)
        } else {
            funBuilder.addStatement("val wrappedHandler = %T.wrapHandlerRecords(telemetry, %L, handler)", KafkaClassNames.handlerWrapper, consumerParameter == null)
        }
        funBuilder.controlFlow("if (config.driverProperties().getProperty(%T.GROUP_ID_CONFIG) == null)", KafkaClassNames.commonClientConfigs) {
            addStatement("val topics = config.topics()")
            addStatement("require(topics != null)")
            addStatement("require(topics.size == 1)")
            addStatement("return %T(config, topics[0], keyDeserializer, valueDeserializer, telemetry, wrappedHandler)", KafkaClassNames.kafkaAssignConsumerContainer)
            nextControlFlow("else")
            addStatement("return %T(config, keyDeserializer, valueDeserializer, wrappedHandler)", KafkaClassNames.kafkaSubscribeConsumerContainer)
        }
        return funBuilder.build()
    }
}
