package io.koraframework.database.common;

import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import org.jspecify.annotations.Nullable;
import io.koraframework.common.DefaultComponent;
import io.koraframework.database.common.telemetry.DefaultDataBaseTelemetryFactory;

public interface DataBaseModule {

    @DefaultComponent
    default DefaultDataBaseTelemetryFactory defaultDataBaseTelemetry(@Nullable Tracer tracer, @Nullable MeterRegistry meterRegistry) {
        return new DefaultDataBaseTelemetryFactory(tracer, meterRegistry);
    }
}
