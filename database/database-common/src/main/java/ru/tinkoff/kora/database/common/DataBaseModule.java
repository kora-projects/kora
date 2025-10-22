package ru.tinkoff.kora.database.common;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.common.DefaultComponent;
import ru.tinkoff.kora.database.common.telemetry.DefaultDataBaseTelemetryFactory;

import java.util.Objects;

public interface DataBaseModule {

    @Nonnull
    @DefaultComponent
    default DefaultDataBaseTelemetryFactory defaultDataBaseTelemetry(Tracer tracer, @Nullable MeterRegistry meterRegistry) {
        return new DefaultDataBaseTelemetryFactory(tracer, Objects.requireNonNullElse(meterRegistry, new CompositeMeterRegistry()));
    }
}
