package ru.tinkoff.kora.kafka.annotation.processor;

import com.squareup.javapoet.ClassName;

public class KafkaClassNames {
    public static final ClassName consumer = ClassName.get("org.apache.kafka.clients.consumer", "Consumer");
    public static final ClassName consumerRecord = ClassName.get("org.apache.kafka.clients.consumer", "ConsumerRecord");
    public static final ClassName consumerRecords = ClassName.get("org.apache.kafka.clients.consumer", "ConsumerRecords");
    public static final ClassName deserializer = ClassName.get("org.apache.kafka.common.serialization", "Deserializer");
    public static final ClassName commonClientConfigs = ClassName.get("org.apache.kafka.clients", "CommonClientConfigs");


    public static final ClassName kafkaListener = ClassName.get("ru.tinkoff.kora.kafka.common.annotation", "KafkaListener");
    public static final ClassName kafkaConsumerConfig = ClassName.get("ru.tinkoff.kora.kafka.common.config", "KafkaConsumerConfig");
    public static final ClassName kafkaSubscribeConsumerContainer = ClassName.get("ru.tinkoff.kora.kafka.common.consumer.containers", "KafkaSubscribeConsumerContainer");
    public static final ClassName kafkaAssignConsumerContainer = ClassName.get("ru.tinkoff.kora.kafka.common.consumer.containers", "KafkaAssignConsumerContainer");
    public static final ClassName handlerWrapper = ClassName.get("ru.tinkoff.kora.kafka.common.consumer.containers.handlers.wrapper", "HandlerWrapper");
    public static final ClassName kafkaConsumerTelemetry = ClassName.get("ru.tinkoff.kora.kafka.common.consumer.telemetry", "KafkaConsumerTelemetry");
    public static final ClassName kafkaConsumerRecordsTelemetry = kafkaConsumerTelemetry.nestedClass("KafkaConsumerRecordsTelemetryContext");
    public static final ClassName kafkaConsumerRecordTelemetry = kafkaConsumerTelemetry.nestedClass("KafkaConsumerRecordTelemetryContext");
    public static final ClassName recordKeyDeserializationException = ClassName.get("ru.tinkoff.kora.kafka.common.exceptions", "RecordKeyDeserializationException");
    public static final ClassName recordValueDeserializationException = ClassName.get("ru.tinkoff.kora.kafka.common.exceptions", "RecordValueDeserializationException");

    public static final ClassName recordHandler = ClassName.get("ru.tinkoff.kora.kafka.common.consumer.containers.handlers", "KafkaRecordHandler");
    public static final ClassName recordsHandler = ClassName.get("ru.tinkoff.kora.kafka.common.consumer.containers.handlers", "KafkaRecordsHandler");


}
