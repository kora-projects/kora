package ru.tinkoff.kora.kafka.symbol.processor

import com.squareup.kotlinpoet.ClassName

object KafkaClassNames {
    val consumer = ClassName("org.apache.kafka.clients.consumer", "Consumer")
    val consumerRecord = ClassName("org.apache.kafka.clients.consumer", "ConsumerRecord")
    val consumerRecords = ClassName("org.apache.kafka.clients.consumer", "ConsumerRecords")
    val deserializer = ClassName("org.apache.kafka.common.serialization", "Deserializer")
    val serializer = ClassName("org.apache.kafka.common.serialization", "Serializer")
    val commonClientConfigs = ClassName("org.apache.kafka.clients", "CommonClientConfigs")


    val kafkaListener = ClassName("ru.tinkoff.kora.kafka.common.annotation", "KafkaListener")
    val kafkaConsumerConfig = ClassName("ru.tinkoff.kora.kafka.common.config", "KafkaConsumerConfig")
    val kafkaSubscribeConsumerContainer = ClassName("ru.tinkoff.kora.kafka.common.consumer.containers", "KafkaSubscribeConsumerContainer")
    val kafkaAssignConsumerContainer = ClassName("ru.tinkoff.kora.kafka.common.consumer.containers", "KafkaAssignConsumerContainer")
    val handlerWrapper = ClassName("ru.tinkoff.kora.kafka.common.consumer.containers.handlers.wrapper", "HandlerWrapper")
    val kafkaConsumerTelemetry = ClassName("ru.tinkoff.kora.kafka.common.consumer.telemetry", "KafkaConsumerTelemetry")
    val kafkaConsumerRecordsTelemetry = kafkaConsumerTelemetry.nestedClass("KafkaConsumerRecordsTelemetryContext")
    val kafkaConsumerRecordTelemetry = kafkaConsumerTelemetry.nestedClass("KafkaConsumerRecordTelemetryContext")
    val recordKeyDeserializationException = ClassName("ru.tinkoff.kora.kafka.common.exceptions", "RecordKeyDeserializationException")
    val recordValueDeserializationException = ClassName("ru.tinkoff.kora.kafka.common.exceptions", "RecordValueDeserializationException")

    val recordHandler = ClassName("ru.tinkoff.kora.kafka.common.consumer.containers.handlers", "KafkaRecordHandler")
    val recordsHandler = ClassName("ru.tinkoff.kora.kafka.common.consumer.containers.handlers", "KafkaRecordsHandler")

    val kafkaPublisherAnnotation = ClassName("ru.tinkoff.kora.kafka.common.annotation", "KafkaPublisher")
    val producer = ClassName("org.apache.kafka.clients.producer", "Producer")
    val kafkaProducer = ClassName("org.apache.kafka.clients.producer", "KafkaProducer")
    val transactionalProducer = ClassName("ru.tinkoff.kora.kafka.common.producer", "TransactionalProducer")
    val transactionalProducerImpl = ClassName("ru.tinkoff.kora.kafka.common.producer", "TransactionalProducerImpl")
    val publisherConfig = ClassName("ru.tinkoff.kora.kafka.common.producer", "PublisherConfig")
    val producerTelemetryFactory = ClassName("ru.tinkoff.kora.kafka.common.producer.telemetry", "KafkaProducerTelemetryFactory")
    val producerTelemetry = ClassName("ru.tinkoff.kora.kafka.common.producer.telemetry", "KafkaProducerTelemetry")
    val producerRecord = ClassName("org.apache.kafka.clients.producer", "ProducerRecord")

}
