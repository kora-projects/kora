package ru.tinkoff.kora.kafka.symbol.processor

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
    val producerRecordMetadata = ClassName("org.apache.kafka.clients.producer", "RecordMetadata")


    val kafkaListener = ClassName("ru.tinkoff.kora.kafka.common.annotation", "KafkaListener")
    val kafkaConsumerConfig = ClassName("ru.tinkoff.kora.kafka.common.consumer", "KafkaListenerConfig")
    val kafkaSubscribeConsumerContainer = ClassName("ru.tinkoff.kora.kafka.common.consumer.containers", "KafkaSubscribeConsumerContainer")
    val kafkaAssignConsumerContainer = ClassName("ru.tinkoff.kora.kafka.common.consumer.containers", "KafkaAssignConsumerContainer")
    val handlerWrapper = ClassName("ru.tinkoff.kora.kafka.common.consumer.containers.handlers.wrapper", "HandlerWrapper")
    val kafkaConsumerTelemetry = ClassName("ru.tinkoff.kora.kafka.common.consumer.telemetry", "KafkaConsumerTelemetry")
    val kafkaConsumerTelemetryFactory = ClassName("ru.tinkoff.kora.kafka.common.consumer.telemetry", "KafkaConsumerTelemetryFactory")
    val consumerRebalanceListener = ClassName("ru.tinkoff.kora.kafka.common.consumer", "ConsumerAwareRebalanceListener")
    val kafkaConsumerRecordsTelemetry = kafkaConsumerTelemetry.nestedClass("KafkaConsumerRecordsTelemetryContext")
    val kafkaConsumerRecordTelemetry = kafkaConsumerTelemetry.nestedClass("KafkaConsumerRecordTelemetryContext")
    val recordKeyDeserializationException = ClassName("ru.tinkoff.kora.kafka.common.exceptions", "RecordKeyDeserializationException")
    val recordValueDeserializationException = ClassName("ru.tinkoff.kora.kafka.common.exceptions", "RecordValueDeserializationException")
    val recordPublisherException = ClassName("ru.tinkoff.kora.kafka.common.exceptions", "KafkaPublishException")
    val recordSerializationException = ClassName("org.apache.kafka.common.errors", "SerializationException")
    val kafkaException = ClassName("org.apache.kafka.common", "KafkaException")


    val recordHandler = ClassName("ru.tinkoff.kora.kafka.common.consumer.containers.handlers", "KafkaRecordHandler")
    val recordsHandler = ClassName("ru.tinkoff.kora.kafka.common.consumer.containers.handlers", "KafkaRecordsHandler")

    val kafkaPublisherAnnotation = ClassName("ru.tinkoff.kora.kafka.common.annotation", "KafkaPublisher");
    val kafkaTopicAnnotation = ClassName("ru.tinkoff.kora.kafka.common.annotation", "KafkaPublisher", "Topic");
    val producer = ClassName("org.apache.kafka.clients.producer", "Producer");
    val producerCallback = ClassName("org.apache.kafka.clients.producer", "Callback");
    val kafkaProducer = ClassName("org.apache.kafka.clients.producer", "KafkaProducer");

    val transactionalPublisher = ClassName("ru.tinkoff.kora.kafka.common.producer", "TransactionalPublisher");
    val transactionalPublisherImpl = ClassName("ru.tinkoff.kora.kafka.common.producer", "TransactionalPublisherImpl");
    val transaction = transactionalPublisher.nestedClass("Transaction");
    val publisherConfig = ClassName("ru.tinkoff.kora.kafka.common.producer", "KafkaPublisherConfig");
    val publisherTransactionalConfig = ClassName("ru.tinkoff.kora.kafka.common.producer", "KafkaPublisherConfig", "TransactionConfig");
    val publisherTopicConfig = ClassName("ru.tinkoff.kora.kafka.common.producer", "KafkaPublisherConfig", "TopicConfig");
    val producerTelemetryFactory = ClassName("ru.tinkoff.kora.kafka.common.producer.telemetry", "KafkaProducerTelemetryFactory");
    val producerTelemetry = ClassName("ru.tinkoff.kora.kafka.common.producer.telemetry", "KafkaProducerTelemetry");
    val telemetryProducerRecord = producerTelemetry.nestedClass("TelemetryProducerRecord");
    val generatedPublisher = ClassName("ru.tinkoff.kora.kafka.common.producer", "GeneratedPublisher");

}
