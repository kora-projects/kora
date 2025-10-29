package ru.tinkoff.kora.kafka.common;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.common.DefaultComponent;
import ru.tinkoff.kora.kafka.common.consumer.telemetry.DefaultKafkaConsumerTelemetryFactory;
import ru.tinkoff.kora.kafka.common.producer.telemetry.DefaultKafkaPublisherTelemetryFactory;

public interface KafkaModule extends KafkaDeserializersModule, KafkaSerializersModule {
    @DefaultComponent
    default DefaultKafkaConsumerTelemetryFactory defaultKafkaConsumerTelemetryFactory(@Nullable Tracer tracer, @Nullable MeterRegistry meterRegistry) {
        if (tracer == null) {
            tracer = TracerProvider.noop().get("kafkaPublisherTelemetry");
        }
        if (meterRegistry == null) {
            meterRegistry = new CompositeMeterRegistry();
        }

        return new DefaultKafkaConsumerTelemetryFactory(tracer, meterRegistry);
    }

    @DefaultComponent
    default DefaultKafkaPublisherTelemetryFactory defaultKafkaProducerTelemetryFactory(@Nullable Tracer tracer, @Nullable MeterRegistry meterRegistry) {
        if (tracer == null) {
            tracer = TracerProvider.noop().get("kafkaPublisherTelemetry");
        }
        if (meterRegistry == null) {
            meterRegistry = new CompositeMeterRegistry();
        }
        return new DefaultKafkaPublisherTelemetryFactory(tracer, meterRegistry);
    }
}
