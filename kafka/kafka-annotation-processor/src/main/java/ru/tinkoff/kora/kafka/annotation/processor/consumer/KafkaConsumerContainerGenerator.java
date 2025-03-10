package ru.tinkoff.kora.kafka.annotation.processor.consumer;

import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.ParameterSpec;
import com.palantir.javapoet.ParameterizedTypeName;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.annotation.processor.common.AnnotationUtils;
import ru.tinkoff.kora.annotation.processor.common.CommonClassNames;
import ru.tinkoff.kora.annotation.processor.common.TagUtils;
import ru.tinkoff.kora.kafka.annotation.processor.consumer.KafkaConsumerHandlerGenerator.HandlerMethod;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.util.Elements;
import java.util.List;
import java.util.Objects;

import static ru.tinkoff.kora.kafka.annotation.processor.KafkaClassNames.*;
import static ru.tinkoff.kora.kafka.annotation.processor.utils.KafkaUtils.getConsumerTags;
import static ru.tinkoff.kora.kafka.annotation.processor.utils.KafkaUtils.prepareMethodName;

public class KafkaConsumerContainerGenerator {

    public MethodSpec generate(Elements elements, ExecutableElement executableElement, AnnotationMirror listenerAnnotation, HandlerMethod handlerMethod, List<ConsumerParameter> parameters) {
        var consumerTags = getConsumerTags(elements, executableElement);
        var tagAnnotation = TagUtils.makeAnnotationSpecForTypes(consumerTags);

        var methodBuilder = MethodSpec.methodBuilder(prepareMethodName(executableElement, "Container"))
            .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
            .addAnnotation(CommonClassNames.root)
            .addAnnotation(tagAnnotation)
            .returns(CommonClassNames.lifecycle);
        var handlerTypeName = (ParameterizedTypeName) handlerMethod.method().returnType();

        var configParameter = ParameterSpec
            .builder(kafkaConsumerConfig, "config")
            .addAnnotation(tagAnnotation)
            .build();
        methodBuilder.addParameter(configParameter);

        var handlerParameter = ParameterSpec
            .builder(ParameterizedTypeName.get(CommonClassNames.valueOf, handlerTypeName), "handler")
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
        methodBuilder.addParameter(ParameterizedTypeName.get(kafkaConsumerTelemetryFactory, handlerMethod.keyType(), handlerMethod.valueType()), "telemetryFactory");
        methodBuilder.addParameter(ParameterSpec.builder(consumerRebalanceListener, "rebalanceListener")
            .addAnnotation(tagAnnotation)
            .addAnnotation(Nullable.class)
            .build());

        var configPath = Objects.requireNonNull(AnnotationUtils.parseAnnotationValueWithoutDefault(listenerAnnotation, "value")).toString();
        methodBuilder.addStatement("var telemetry = telemetryFactory.get($S, config.driverProperties(), config.telemetry())", configPath);

        var consumerParameter = parameters.stream().filter(r -> r instanceof ConsumerParameter.Consumer).map(ConsumerParameter.Consumer.class::cast).findFirst();
        if (handlerTypeName.rawType().equals(recordHandler)) {
            methodBuilder.addCode("var wrappedHandler = $T.wrapHandlerRecord(telemetry, $L, handler);\n", handlerWrapper, consumerParameter.isEmpty());
        } else {
            methodBuilder.addCode("var wrappedHandler = $T.wrapHandlerRecords(telemetry, $L, handler, config.allowEmptyRecords());\n", handlerWrapper, consumerParameter.isEmpty());
        }
        methodBuilder.addCode("if (config.driverProperties().getProperty($T.GROUP_ID_CONFIG) == null) {$>\n", commonClientConfigs);
        methodBuilder.beginControlFlow("if (config.topics() == null || config.topics().size() != 1)"); // todo allow list?
        methodBuilder.addStatement("throw new java.lang.IllegalArgumentException($S + config.topics())", "@KafkaListener require to specify 1 topic to subscribe when groupId is null, but received: ");
        methodBuilder.endControlFlow();
        methodBuilder.addCode("return new $T<>($S, config, config.topics().get(0), keyDeserializer, valueDeserializer, telemetry, wrappedHandler);",
            kafkaAssignConsumerContainer, configPath);
        methodBuilder.addCode("$<\n} else {$>\n");
        methodBuilder.addCode("return new $T<>($S, config, keyDeserializer, valueDeserializer, wrappedHandler, rebalanceListener);",
            kafkaSubscribeConsumerContainer, configPath);
        methodBuilder.addCode("$<\n}\n");
        return methodBuilder.build();
    }
}
