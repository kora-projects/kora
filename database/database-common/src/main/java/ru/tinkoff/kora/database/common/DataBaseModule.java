package ru.tinkoff.kora.database.common;

import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import org.jspecify.annotations.Nullable;
import ru.tinkoff.kora.common.DefaultComponent;
import ru.tinkoff.kora.database.common.telemetry.DefaultDataBaseTelemetryFactory;

public interface DataBaseModule {

    @DefaultComponent
    default DefaultDataBaseTelemetryFactory defaultDataBaseTelemetry(@Nullable Tracer tracer, @Nullable MeterRegistry meterRegistry) {
        return new DefaultDataBaseTelemetryFactory(tracer, meterRegistry);
    }
}
