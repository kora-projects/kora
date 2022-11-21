package ru.tinkoff.kora.kafka.annotation.processor;

import com.squareup.javapoet.ClassName;

public class KafkaClassNames {
    public static final ClassName consumer = ClassName.get("org.apache.kafka.clients.consumer", "Consumer");
    public static final ClassName consumerRecord = ClassName.get("org.apache.kafka.clients.consumer", "ConsumerRecord");
    public static final ClassName consumerRecords = ClassName.get("org.apache.kafka.clients.consumer", "ConsumerRecords");
    public static final ClassName deserializer = ClassName.get("org.apache.kafka.common.serialization", "Deserializer");


    public static final ClassName kafkaIncoming = ClassName.get("ru.tinkoff.kora.kafka.common.annotation", "KafkaIncoming");
    public static final ClassName kafkaConsumerConfig = ClassName.get("ru.tinkoff.kora.kafka.common.config", "KafkaConsumerConfig");
    public static final ClassName kafkaConsumerContainer = ClassName.get("ru.tinkoff.kora.kafka.common.containers", "KafkaConsumerContainer");
    public static final ClassName handlerWrapper = ClassName.get("ru.tinkoff.kora.kafka.common.containers.handlers.wrapper", "HandlerWrapper");
    public static final ClassName kafkaConsumerTelemetry = ClassName.get("ru.tinkoff.kora.kafka.common.telemetry", "KafkaConsumerTelemetry");

}
