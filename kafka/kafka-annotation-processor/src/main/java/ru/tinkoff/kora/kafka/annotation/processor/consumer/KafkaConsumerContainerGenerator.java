package ru.tinkoff.kora.kafka.annotation.processor.consumer;

import com.squareup.javapoet.*;
import ru.tinkoff.kora.annotation.processor.common.CommonClassNames;
import ru.tinkoff.kora.annotation.processor.common.TagUtils;
import ru.tinkoff.kora.kafka.annotation.processor.consumer.KafkaConsumerHandlerGenerator.HandlerMethod;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import java.util.List;

import static ru.tinkoff.kora.kafka.annotation.processor.KafkaClassNames.*;
import static ru.tinkoff.kora.kafka.annotation.processor.utils.KafkaUtils.prepareMethodName;
import static ru.tinkoff.kora.kafka.annotation.processor.utils.KafkaUtils.prepareTagName;

public class KafkaConsumerContainerGenerator {
    public MethodSpec generate(ExecutableElement executableElement, HandlerMethod handlerMethod, List<ConsumerParameter> parameters) {
        var methodBuilder = MethodSpec.methodBuilder(prepareMethodName(executableElement, "Container"))
            .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
            .addAnnotation(CommonClassNames.root)
            .returns(CommonClassNames.lifecycle);

        var tagName = prepareTagName(executableElement);
        var tagsBlock = CodeBlock.of("$L.class", tagName);
        var tagAnnotation = AnnotationSpec.builder(CommonClassNames.tag).addMember("value", tagsBlock).build();
        var configParameter = ParameterSpec
            .builder(kafkaConsumerConfig, "config")
            .addAnnotation(tagAnnotation)
            .build();
        methodBuilder.addParameter(configParameter);

        var handlerParameter = ParameterSpec
            .builder(handlerMethod.method().returnType, "handler")
            .addAnnotation(tagAnnotation)
            .build();
        methodBuilder.addParameter(handlerParameter);

        var keyDeserializer = ParameterSpec.builder(ParameterizedTypeName.get(deserializer, handlerMethod.keyType()), "keyDeserializer");
        if (!handlerMethod.keyTag().isEmpty()) {
            var keyTag = TagUtils.makeAnnotationSpec(handlerMethod.keyTag());
            keyDeserializer.addAnnotation(keyTag);
        }

        var valueDeserializer = ParameterSpec.builder(ParameterizedTypeName.get(deserializer, handlerMethod.valueType()), "valueDeserializer");
        if (!handlerMethod.valueTag().isEmpty()) {
            var valueTag = TagUtils.makeAnnotationSpec(handlerMethod.valueTag());
            valueDeserializer.addAnnotation(valueTag);
        }

        methodBuilder.addParameter(keyDeserializer.build());
        methodBuilder.addParameter(valueDeserializer.build());
        methodBuilder.addParameter(ParameterizedTypeName.get(kafkaConsumerTelemetry, handlerMethod.keyType(), handlerMethod.valueType()), "telemetry");


        var consumerParameter = parameters.stream().filter(r -> r instanceof ConsumerParameter.Consumer).map(ConsumerParameter.Consumer.class::cast).findFirst().orElse(null);
        methodBuilder.addCode("var wrappedHandler = $T.wrapHandler(telemetry, $L, handler);\n", handlerWrapper, consumerParameter == null);
        methodBuilder.addCode("if (config.driverProperties().getProperty($T.GROUP_ID_CONFIG) == null) {$>\n", commonClientConfigs);
        methodBuilder.addCode("assert config.topics().size() == 1;\n"); // todo allow list?
        methodBuilder.addCode("return new $T<>(config, config.topics().get(0), keyDeserializer, valueDeserializer, telemetry, wrappedHandler);", kafkaAssignConsumerContainer);
        methodBuilder.addCode("$<\n} else {$>\n");
        methodBuilder.addCode("return new $T<>(config, keyDeserializer, valueDeserializer, wrappedHandler);", kafkaSubscribeConsumerContainer);
        methodBuilder.addCode("$<\n}\n");
        return methodBuilder.build();
    }
}
