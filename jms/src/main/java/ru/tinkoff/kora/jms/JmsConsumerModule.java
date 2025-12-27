package ru.tinkoff.kora.jms;

import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import org.jspecify.annotations.Nullable;
import ru.tinkoff.kora.common.DefaultComponent;
import ru.tinkoff.kora.jms.telemetry.DefaultJmsConsumerTelemetryFactory;

public interface JmsConsumerModule {
    @DefaultComponent
    default DefaultJmsConsumerTelemetryFactory defaultJmsConsumerTelemetryFactory(@Nullable Tracer tracer, @Nullable MeterRegistry meterRegistry) {
        return new DefaultJmsConsumerTelemetryFactory(tracer, meterRegistry);
    }
}
