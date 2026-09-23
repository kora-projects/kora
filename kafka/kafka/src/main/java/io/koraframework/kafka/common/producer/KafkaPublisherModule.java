package io.koraframework.kafka.common.producer;

import io.koraframework.common.annotation.DefaultComponent;
import io.koraframework.common.annotation.Tag;
import io.koraframework.kafka.common.producer.serializer.KafkaSerializersModule;
import io.koraframework.kafka.common.producer.telemetry.KafkaPublisherTelemetryFactory;
import io.koraframework.kafka.common.producer.telemetry.KafkaPublisherTelemetry;
import io.koraframework.kafka.common.producer.telemetry.impl.DefaultKafkaPublisherLoggerFactory;
import io.koraframework.kafka.common.producer.telemetry.impl.DefaultKafkaPublisherMetricsFactory;
import io.koraframework.kafka.common.producer.telemetry.impl.DefaultKafkaPublisherTelemetryFactory;
import io.koraframework.logging.common.masking.MaskingStrategy;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import org.jspecify.annotations.Nullable;

public interface KafkaPublisherModule extends KafkaSerializersModule {

    @Tag(KafkaPublisherTelemetry.class)
    @DefaultComponent
    default MaskingStrategy defaultKafkaProducerArgMaskingStrategy() {
        return value -> "***";
    }

    @DefaultComponent
    default DefaultKafkaPublisherLoggerFactory defaultKafkaPublisherLoggerFactory(@Tag(KafkaPublisherTelemetry.class) MaskingStrategy maskingStrategy) {
        return new DefaultKafkaPublisherLoggerFactory(maskingStrategy);
    }

    @DefaultComponent
    default KafkaPublisherTelemetryFactory defaultKafkaPublisherTelemetryFactory(@Nullable Tracer tracer,
                                                                                 @Nullable MeterRegistry meterRegistry,
                                                                                 @Nullable DefaultKafkaPublisherLoggerFactory loggerFactory,
                                                                                 @Nullable DefaultKafkaPublisherMetricsFactory metricsFactory) {
        return new DefaultKafkaPublisherTelemetryFactory(tracer, meterRegistry, loggerFactory, metricsFactory);
    }
}
