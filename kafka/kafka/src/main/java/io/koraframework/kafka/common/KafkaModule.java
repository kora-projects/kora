package io.koraframework.kafka.common;

import io.koraframework.common.DefaultComponent;
import io.koraframework.kafka.common.consumer.telemetry.DefaultKafkaConsumerMetricsFactory;
import io.koraframework.kafka.common.consumer.telemetry.DefaultKafkaConsumerTelemetryFactory;
import io.koraframework.kafka.common.producer.telemetry.DefaultKafkaPublisherMetricsFactory;
import io.koraframework.kafka.common.producer.telemetry.DefaultKafkaPublisherTelemetryFactory;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import org.jspecify.annotations.Nullable;

public interface KafkaModule extends KafkaDeserializersModule, KafkaSerializersModule {

    @DefaultComponent
    default DefaultKafkaConsumerTelemetryFactory defaultKafkaConsumerTelemetryFactory(@Nullable Tracer tracer,
                                                                                      @Nullable MeterRegistry meterRegistry,
                                                                                      @Nullable DefaultKafkaConsumerMetricsFactory metricsFactory) {
        return new DefaultKafkaConsumerTelemetryFactory(tracer, meterRegistry, metricsFactory);
    }

    @DefaultComponent
    default DefaultKafkaPublisherTelemetryFactory defaultKafkaPublisherTelemetryFactory(@Nullable Tracer tracer,
                                                                                        @Nullable MeterRegistry meterRegistry,
                                                                                        @Nullable DefaultKafkaPublisherMetricsFactory metricsFactory) {
        return new DefaultKafkaPublisherTelemetryFactory(tracer, meterRegistry, metricsFactory);
    }
}
