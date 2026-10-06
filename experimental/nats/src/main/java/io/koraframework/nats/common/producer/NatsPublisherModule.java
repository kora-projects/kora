package io.koraframework.nats.common.producer;

import io.koraframework.common.annotation.DefaultComponent;
import io.koraframework.common.annotation.Tag;
import io.koraframework.logging.common.masking.MaskingStrategy;
import io.koraframework.nats.common.producer.serializer.NatsSerializersModule;
import io.koraframework.nats.common.producer.telemetry.NatsPublisherTelemetry;
import io.koraframework.nats.common.producer.telemetry.NatsPublisherTelemetryFactory;
import io.koraframework.nats.common.producer.telemetry.impl.DefaultNatsPublisherLoggerFactory;
import io.koraframework.nats.common.producer.telemetry.impl.DefaultNatsPublisherMetricsFactory;
import io.koraframework.nats.common.producer.telemetry.impl.DefaultNatsPublisherTelemetryFactory;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import org.jspecify.annotations.Nullable;

public interface NatsPublisherModule extends NatsSerializersModule {
    @DefaultComponent
    @Tag(NatsPublisherTelemetry.class)
    default MaskingStrategy natsPublisherMaskingStrategy() {
        return value -> "***";
    }

    @DefaultComponent
    default DefaultNatsPublisherLoggerFactory natsPublisherLoggerFactory(@Tag(NatsPublisherTelemetry.class) MaskingStrategy masking) {
        return new DefaultNatsPublisherLoggerFactory(masking);
    }

    @DefaultComponent
    default NatsPublisherTelemetryFactory natsPublisherTelemetryFactory(@Nullable Tracer tracer, @Nullable MeterRegistry registry,
                                                                        @Nullable DefaultNatsPublisherLoggerFactory loggerFactory, @Nullable DefaultNatsPublisherMetricsFactory metricsFactory) {
        return new DefaultNatsPublisherTelemetryFactory(tracer, registry, loggerFactory, metricsFactory);
    }
}
