package ru.tinkoff.kora.kafka.common;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.common.DefaultComponent;
import ru.tinkoff.kora.kafka.common.consumer.telemetry.DefaultKafkaConsumerTelemetryFactory;
import ru.tinkoff.kora.kafka.common.consumer.telemetry.KafkaConsumerLoggerFactory;
import ru.tinkoff.kora.kafka.common.consumer.telemetry.KafkaConsumerMetricsFactory;
import ru.tinkoff.kora.kafka.common.consumer.telemetry.KafkaConsumerTracerFactory;
import ru.tinkoff.kora.kafka.common.producer.telemetry.*;

public interface KafkaModule extends KafkaDeserializersModule, KafkaSerializersModule {
    @DefaultComponent
    default <K, V> DefaultKafkaConsumerTelemetryFactory<K, V> defaultKafkaConsumerTelemetryFactory(@Nullable KafkaConsumerLoggerFactory<K, V> logger, @Nullable KafkaConsumerTracerFactory tracing, @Nullable KafkaConsumerMetricsFactory metrics) {
        return new DefaultKafkaConsumerTelemetryFactory<>(logger, metrics, tracing);
    }

    @DefaultComponent
    default DefaultKafkaProducerTelemetryFactory defaultKafkaProducerTelemetryFactory(@Nullable KafkaProducerTracerFactory tracer, @Nullable KafkaProducerLoggerFactory logger, @Nullable KafkaProducerMetricsFactory metrics) {
        return new DefaultKafkaProducerTelemetryFactory(tracer, logger, metrics);
    }

    @DefaultComponent
    default DefaultKafkaProducerLoggerFactory defaultKafkaProducerLoggerFactory() {
        return new DefaultKafkaProducerLoggerFactory();
    }
}
