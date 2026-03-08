package io.koraframework.kafka.annotation.processor;

import com.palantir.javapoet.ClassName;

public final class KafkaClassNames {

    private KafkaClassNames() {}

    public static final ClassName consumer = ClassName.get("org.apache.kafka.clients.consumer", "Consumer");
    public static final ClassName consumerRecord = ClassName.get("org.apache.kafka.clients.consumer", "ConsumerRecord");
    public static final ClassName producerRecord = ClassName.get("org.apache.kafka.clients.producer", "ProducerRecord");
    public static final ClassName telemetryProducerRecord = ClassName.get("io.koraframework.kafka.common.producer.telemetry", "KafkaProducerTelemetry", "TelemetryProducerRecord");
    public static final ClassName consumerRecords = ClassName.get("org.apache.kafka.clients.consumer", "ConsumerRecords");
    public static final ClassName deserializer = ClassName.get("org.apache.kafka.common.serialization", "Deserializer");
    public static final ClassName serializer = ClassName.get("org.apache.kafka.common.serialization", "Serializer");
    public static final ClassName byteArraySerializer = ClassName.get("org.apache.kafka.common.serialization", "ByteArraySerializer");
    public static final ClassName commonClientConfigs = ClassName.get("org.apache.kafka.clients", "CommonClientConfigs");
    public static final ClassName headers = ClassName.get("org.apache.kafka.common.header", "Headers");
    public static final ClassName recordHeaders = ClassName.get("org.apache.kafka.common.header.internals", "RecordHeaders");
    public static final ClassName recordMetadata = ClassName.get("org.apache.kafka.clients.producer", "RecordMetadata");
    public static final ClassName header = ClassName.get("org.apache.kafka.common.header", "Header");


    public static final ClassName kafkaListener = ClassName.get("io.koraframework.kafka.common.annotation", "KafkaListener");
    public static final ClassName kafkaConsumerConfig = ClassName.get("io.koraframework.kafka.common.consumer", "KafkaListenerConfig");
    public static final ClassName kafkaSubscribeConsumerContainer = ClassName.get("io.koraframework.kafka.common.consumer.containers", "KafkaSubscribeConsumerContainer");
    public static final ClassName kafkaAssignConsumerContainer = ClassName.get("io.koraframework.kafka.common.consumer.containers", "KafkaAssignConsumerContainer");
    public static final ClassName consumerRebalanceListener = ClassName.get("io.koraframework.kafka.common.consumer", "ConsumerAwareRebalanceListener");
    public static final ClassName handlerWrapper = ClassName.get("io.koraframework.kafka.common.consumer.containers.handlers.wrapper", "HandlerWrapper");
    public static final ClassName kafkaConsumerTelemetry = ClassName.get("io.koraframework.kafka.common.consumer.telemetry", "KafkaConsumerTelemetry");
    public static final ClassName kafkaConsumerTelemetryFactory = ClassName.get("io.koraframework.kafka.common.consumer.telemetry", "KafkaConsumerTelemetryFactory");
    public static final ClassName recordKeyDeserializationException = ClassName.get("io.koraframework.kafka.common.exceptions", "RecordKeyDeserializationException");
    public static final ClassName recordValueDeserializationException = ClassName.get("io.koraframework.kafka.common.exceptions", "RecordValueDeserializationException");
    public static final ClassName recordPublisherException = ClassName.get("io.koraframework.kafka.common.exceptions", "KafkaPublishException");


    public static final ClassName recordHandler = ClassName.get("io.koraframework.kafka.common.consumer.containers.handlers", "KafkaRecordHandler");
    public static final ClassName recordsHandler = ClassName.get("io.koraframework.kafka.common.consumer.containers.handlers", "KafkaRecordsHandler");


    public static final ClassName kafkaPublisherAnnotation = ClassName.get("io.koraframework.kafka.common.annotation", "KafkaPublisher");
    public static final ClassName kafkaTopicAnnotation = ClassName.get("io.koraframework.kafka.common.annotation", "KafkaPublisher", "Topic");
    public static final ClassName producer = ClassName.get("org.apache.kafka.clients.producer", "Producer");
    public static final ClassName producerCallback = ClassName.get("org.apache.kafka.clients.producer", "Callback");
    public static final ClassName kafkaProducer = ClassName.get("org.apache.kafka.clients.producer", "KafkaProducer");

    public static final ClassName transactionalPublisher = ClassName.get("io.koraframework.kafka.common.producer", "TransactionalPublisher");
    public static final ClassName transactionalPublisherImpl = ClassName.get("io.koraframework.kafka.common.producer", "TransactionalPublisherImpl");
    public static final ClassName transaction = transactionalPublisher.nestedClass("Transaction");
    public static final ClassName publisherConfig = ClassName.get("io.koraframework.kafka.common.producer", "KafkaPublisherConfig");
    public static final ClassName publisherTransactionalConfig = ClassName.get("io.koraframework.kafka.common.producer", "KafkaPublisherConfig", "TransactionConfig");
    public static final ClassName publisherTopicConfig = ClassName.get("io.koraframework.kafka.common.producer", "KafkaPublisherConfig", "TopicConfig");
    public static final ClassName publisherTelemetryConfig = ClassName.get("io.koraframework.kafka.common.producer.telemetry", "KafkaPublisherTelemetryConfig");
    public static final ClassName abstractPublisher = ClassName.get("io.koraframework.kafka.common.producer", "AbstractPublisher");
    public static final ClassName producerTelemetryFactory = ClassName.get("io.koraframework.kafka.common.producer.telemetry", "KafkaPublisherTelemetryFactory");
    public static final ClassName generatedPublisher = ClassName.get("io.koraframework.kafka.common.producer", "GeneratedPublisher");
}
