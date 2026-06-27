package io.koraframework.kafka.common;

import io.koraframework.common.DefaultComponent;
import io.koraframework.kafka.common.consumer.telemetry.KafkaConsumerTelemetryFactory;
import io.koraframework.kafka.common.consumer.telemetry.impl.DefaultKafkaConsumerLoggerFactory;
import io.koraframework.kafka.common.consumer.telemetry.impl.DefaultKafkaConsumerMetricsFactory;
import io.koraframework.kafka.common.consumer.telemetry.impl.DefaultKafkaConsumerTelemetryFactory;
import io.koraframework.kafka.common.producer.telemetry.KafkaPublisherTelemetryFactory;
import io.koraframework.kafka.common.producer.telemetry.impl.DefaultKafkaPublisherLoggerFactory;
import io.koraframework.kafka.common.producer.telemetry.impl.DefaultKafkaPublisherMetricsFactory;
import io.koraframework.kafka.common.producer.telemetry.impl.DefaultKafkaPublisherTelemetryFactory;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import org.jspecify.annotations.Nullable;

public interface KafkaModule extends KafkaDeserializersModule, KafkaSerializersModule {

    @DefaultComponent
    default KafkaConsumerTelemetryFactory defaultKafkaConsumerTelemetryFactory(@Nullable Tracer tracer,
                                                                               @Nullable MeterRegistry meterRegistry,
                                                                               @Nullable DefaultKafkaConsumerLoggerFactory loggerFactory,
                                                                               @Nullable DefaultKafkaConsumerMetricsFactory metricsFactory) {
        return new DefaultKafkaConsumerTelemetryFactory(tracer, meterRegistry, loggerFactory, metricsFactory);
    }

    @DefaultComponent
    default KafkaPublisherTelemetryFactory defaultKafkaPublisherTelemetryFactory(@Nullable Tracer tracer,
                                                                                 @Nullable MeterRegistry meterRegistry,
                                                                                 @Nullable DefaultKafkaPublisherLoggerFactory loggerFactory,
                                                                                 @Nullable DefaultKafkaPublisherMetricsFactory metricsFactory) {
        return new DefaultKafkaPublisherTelemetryFactory(tracer, meterRegistry, loggerFactory, metricsFactory);
    }
}
