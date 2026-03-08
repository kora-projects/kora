package io.koraframework.kafka.common;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;
import org.jspecify.annotations.Nullable;
import io.koraframework.common.DefaultComponent;
import io.koraframework.kafka.common.consumer.telemetry.DefaultKafkaConsumerTelemetryFactory;
import io.koraframework.kafka.common.producer.telemetry.DefaultKafkaPublisherTelemetryFactory;

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
