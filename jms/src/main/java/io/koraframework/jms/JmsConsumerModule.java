package io.koraframework.jms;

import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import org.jspecify.annotations.Nullable;
import io.koraframework.common.DefaultComponent;
import io.koraframework.jms.telemetry.DefaultJmsConsumerTelemetryFactory;

public interface JmsConsumerModule {
    @DefaultComponent
    default DefaultJmsConsumerTelemetryFactory defaultJmsConsumerTelemetryFactory(@Nullable Tracer tracer, @Nullable MeterRegistry meterRegistry) {
        return new DefaultJmsConsumerTelemetryFactory(tracer, meterRegistry);
    }
}
