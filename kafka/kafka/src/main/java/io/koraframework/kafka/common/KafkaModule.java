package io.koraframework.kafka.common;

import io.koraframework.common.DefaultComponent;
import io.koraframework.kafka.common.consumer.telemetry.DefaultKafkaConsumerTelemetryFactory;
import io.koraframework.kafka.common.producer.telemetry.DefaultKafkaPublisherTelemetryFactory;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import org.jspecify.annotations.Nullable;

public interface KafkaModule extends KafkaDeserializersModule, KafkaSerializersModule {

    @DefaultComponent
    default DefaultKafkaConsumerTelemetryFactory defaultKafkaConsumerTelemetryFactory(@Nullable Tracer tracer,
                                                                                      @Nullable MeterRegistry meterRegistry) {
        return new DefaultKafkaConsumerTelemetryFactory(tracer, meterRegistry);
    }

    @DefaultComponent
    default DefaultKafkaPublisherTelemetryFactory defaultKafkaPublisherTelemetryFactory(@Nullable Tracer tracer,
                                                                                        @Nullable MeterRegistry meterRegistry) {
        return new DefaultKafkaPublisherTelemetryFactory(tracer, meterRegistry);
    }
}
