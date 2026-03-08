package io.koraframework.kafka.symbol.processor

import com.squareup.kotlinpoet.ClassName

object KafkaClassNames {
    val consumer = ClassName("org.apache.kafka.clients.consumer", "Consumer")
    val consumerRecord = ClassName("org.apache.kafka.clients.consumer", "ConsumerRecord")
    val consumerRecords = ClassName("org.apache.kafka.clients.consumer", "ConsumerRecords")
    val producerRecord = ClassName("org.apache.kafka.clients.producer", "ProducerRecord");
    val deserializer = ClassName("org.apache.kafka.common.serialization", "Deserializer")
    val serializer = ClassName("org.apache.kafka.common.serialization", "Serializer")
    val commonClientConfigs = ClassName("org.apache.kafka.clients", "CommonClientConfigs")
    val headers = ClassName("org.apache.kafka.common.header", "Headers")
    val byteArraySerializer = ClassName("org.apache.kafka.common.serialization", "ByteArraySerializer")
    val recordHeaders = ClassName("org.apache.kafka.common.header.internals", "RecordHeaders");
    val header = ClassName("org.apache.kafka.common.header", "Header");


    val kafkaListener = ClassName("io.koraframework.kafka.common.annotation", "KafkaListener")
    val kafkaConsumerConfig = ClassName("io.koraframework.kafka.common.consumer", "KafkaListenerConfig")
    val kafkaSubscribeConsumerContainer = ClassName("io.koraframework.kafka.common.consumer.containers", "KafkaSubscribeConsumerContainer")
    val kafkaAssignConsumerContainer = ClassName("io.koraframework.kafka.common.consumer.containers", "KafkaAssignConsumerContainer")
    val handlerWrapper = ClassName("io.koraframework.kafka.common.consumer.containers.handlers.wrapper", "HandlerWrapper")
    val kafkaConsumerTelemetry = ClassName("io.koraframework.kafka.common.consumer.telemetry", "KafkaConsumerTelemetry")
    val kafkaConsumerTelemetryFactory = ClassName("io.koraframework.kafka.common.consumer.telemetry", "KafkaConsumerTelemetryFactory")
    val consumerRebalanceListener = ClassName("io.koraframework.kafka.common.consumer", "ConsumerAwareRebalanceListener")
    val recordKeyDeserializationException = ClassName("io.koraframework.kafka.common.exceptions", "RecordKeyDeserializationException")
    val recordValueDeserializationException = ClassName("io.koraframework.kafka.common.exceptions", "RecordValueDeserializationException")
    val recordPublisherException = ClassName("io.koraframework.kafka.common.exceptions", "KafkaPublishException")
    val recordSerializationException = ClassName("org.apache.kafka.common.errors", "SerializationException")
    val kafkaException = ClassName("org.apache.kafka.common", "KafkaException")


    val recordHandler = ClassName("io.koraframework.kafka.common.consumer.containers.handlers", "KafkaRecordHandler")
    val recordsHandler = ClassName("io.koraframework.kafka.common.consumer.containers.handlers", "KafkaRecordsHandler")

    val kafkaPublisherAnnotation = ClassName("io.koraframework.kafka.common.annotation", "KafkaPublisher");
    val kafkaTopicAnnotation = ClassName("io.koraframework.kafka.common.annotation", "KafkaPublisher", "Topic");
    val producer = ClassName("org.apache.kafka.clients.producer", "Producer");
    val producerCallback = ClassName("org.apache.kafka.clients.producer", "Callback");
    val producerRecordMetadata = ClassName("org.apache.kafka.clients.producer", "RecordMetadata")
    val kafkaProducer = ClassName("org.apache.kafka.clients.producer", "KafkaProducer");

    val transactionalPublisher = ClassName("io.koraframework.kafka.common.producer", "TransactionalPublisher");
    val transactionalPublisherImpl = ClassName("io.koraframework.kafka.common.producer", "TransactionalPublisherImpl");
    val transaction = transactionalPublisher.nestedClass("Transaction");
    val publisherConfig = ClassName("io.koraframework.kafka.common.producer", "KafkaPublisherConfig");
    val publisherTransactionalConfig = ClassName("io.koraframework.kafka.common.producer", "KafkaPublisherConfig", "TransactionConfig");
    val publisherTopicConfig = ClassName("io.koraframework.kafka.common.producer", "KafkaPublisherConfig", "TopicConfig");
    val publisherTelemetryConfig = ClassName("io.koraframework.kafka.common.producer.telemetry", "KafkaPublisherTelemetryConfig");
    val abstractPublisher = ClassName("io.koraframework.kafka.common.producer", "AbstractPublisher");
    val producerTelemetryFactory = ClassName("io.koraframework.kafka.common.producer.telemetry", "KafkaPublisherTelemetryFactory");
    val generatedPublisher = ClassName("io.koraframework.kafka.common.producer", "GeneratedPublisher");

}
