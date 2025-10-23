package ru.tinkoff.kora.kafka.common;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.common.DefaultComponent;
import ru.tinkoff.kora.kafka.common.consumer.telemetry.DefaultKafkaConsumerTelemetryFactory;
import ru.tinkoff.kora.kafka.common.consumer.telemetry.KafkaConsumerLoggerFactory;
import ru.tinkoff.kora.kafka.common.consumer.telemetry.KafkaConsumerMetricsFactory;
import ru.tinkoff.kora.kafka.common.consumer.telemetry.KafkaConsumerTracerFactory;
import ru.tinkoff.kora.kafka.common.producer.telemetry.DefaultKafkaPublisherTelemetryFactory;

public interface KafkaModule extends KafkaDeserializersModule, KafkaSerializersModule {
    @DefaultComponent
    default <K, V> DefaultKafkaConsumerTelemetryFactory<K, V> defaultKafkaConsumerTelemetryFactory(@Nullable KafkaConsumerLoggerFactory<K, V> logger, @Nullable KafkaConsumerTracerFactory tracing, @Nullable KafkaConsumerMetricsFactory metrics) {
        return new DefaultKafkaConsumerTelemetryFactory<>(logger, metrics, tracing);
    }

    @DefaultComponent
    default DefaultKafkaPublisherTelemetryFactory defaultKafkaProducerTelemetryFactory(Tracer tracer, MeterRegistry meterRegistry) {
        if (tracer == null) {
            tracer = TracerProvider.noop().get("kafkaPublisherTelemetry");
        }
        if (meterRegistry == null) {
            meterRegistry = new CompositeMeterRegistry();
        }
        return new DefaultKafkaPublisherTelemetryFactory(tracer, meterRegistry);
    }
}
