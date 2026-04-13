package io.koraframework.kafka.symbol.processor.consumer

import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import io.koraframework.kafka.symbol.processor.KafkaClassNames
import io.koraframework.kafka.symbol.processor.KafkaUtils.consumerTag
import io.koraframework.kafka.symbol.processor.KafkaUtils.containerFunName
import io.koraframework.kafka.symbol.processor.consumer.KafkaHandlerGenerator.HandlerFunction
import io.koraframework.ksp.common.AnnotationUtils.findValueNoDefault
import io.koraframework.ksp.common.CommonClassNames
import io.koraframework.ksp.common.KotlinPoetUtils.controlFlow
import io.koraframework.ksp.common.TagUtils.addTag

class KafkaContainerGenerator {
    fun generate(functionDeclaration: KSFunctionDeclaration, listenerAnnotation: KSAnnotation, handler: HandlerFunction, parameters: List<ConsumerParameter>): FunSpec {
        val keyType = handler.keyType
        val valueType = handler.valueType
        val handlerType = handler.funSpec.returnType as ParameterizedTypeName

        val consumerParameter = parameters.firstOrNull { it is ConsumerParameter.Consumer } as ConsumerParameter.Consumer?
        val consumerTag = functionDeclaration.consumerTag()

        val funBuilder = FunSpec.builder(functionDeclaration.containerFunName())
            .addParameter(ParameterSpec.builder("config", KafkaClassNames.kafkaConsumerConfig).addTag(consumerTag).build())
            .addParameter(ParameterSpec.builder("handler", CommonClassNames.valueOf.parameterizedBy(handlerType)).addTag(consumerTag).build())
            .addParameter(ParameterSpec.builder("keyDeserializer", KafkaClassNames.deserializer.parameterizedBy(keyType)).addTag(handler.keyTag).build())
            .addParameter(ParameterSpec.builder("valueDeserializer", KafkaClassNames.deserializer.parameterizedBy(valueType)).addTag(handler.valueTag).build())
            .addParameter("telemetryFactory", KafkaClassNames.kafkaConsumerTelemetryFactory)
            .addParameter(ParameterSpec.builder("rebalanceListener", KafkaClassNames.consumerRebalanceListener.copy(true))
                .addTag(consumerTag)
                .build())
            .addAnnotation(CommonClassNames.root)
            .addTag(consumerTag)
            .returns(CommonClassNames.lifecycle)

        val configPath = listenerAnnotation.findValueNoDefault<String>("value")!!
        val consumerName = functionDeclaration.parentDeclaration?.qualifiedName?.asString() + "." + functionDeclaration.simpleName.asString()
        funBuilder.addStatement("val telemetry = telemetryFactory.get(%S, %S, config.driverProperties(), config.telemetry())",
            configPath, consumerName)
        if (handlerType.rawType == KafkaClassNames.recordHandler) {
            funBuilder.addStatement("val wrappedHandler = %T.wrapHandlerRecord(%L, handler)", KafkaClassNames.handlerWrapper, consumerParameter == null)
        } else {
            funBuilder.addStatement("val wrappedHandler = %T.wrapHandlerRecords(%L, handler, config.allowEmptyRecords())", KafkaClassNames.handlerWrapper, consumerParameter == null)
        }
        funBuilder.controlFlow("if (config.driverProperties().getProperty(%T.GROUP_ID_CONFIG) == null)", KafkaClassNames.commonClientConfigs) {
            addStatement("val topics = config.topics()")
            addStatement("require(topics != null)")
            addStatement("require(topics.size == 1)")
            addStatement("return %T(%S, %S, config, topics[0], keyDeserializer, valueDeserializer, telemetry, wrappedHandler)",
                KafkaClassNames.kafkaAssignConsumerContainer, configPath, consumerName)
            nextControlFlow("else")
            addStatement("return %T(%S, %S, config, keyDeserializer, valueDeserializer, wrappedHandler, telemetry, rebalanceListener)",
                KafkaClassNames.kafkaSubscribeConsumerContainer, configPath, consumerName)
        }
        return funBuilder.build()
    }
}
