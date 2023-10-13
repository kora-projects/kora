package ru.tinkoff.kora.opentelemetry.module.scheduling;

import io.opentelemetry.api.trace.Tracer;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.scheduling.common.telemetry.SchedulingTracer;
import ru.tinkoff.kora.scheduling.common.telemetry.SchedulingTracerFactory;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.util.Objects;

public class OpentelemetrySchedulingTracerFactory implements SchedulingTracerFactory {
    private final Tracer tracer;

    public OpentelemetrySchedulingTracerFactory(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    @Nullable
    public SchedulingTracer get(TelemetryConfig.TracingConfig tracing, Class<?> jobClass, String jobMethod) {
        if (Objects.requireNonNullElse(tracing.enabled(), true)) {
            return new OpentelemetrySchedulingTracer(this.tracer, jobClass.getCanonicalName(), jobMethod);
        } else {
            return null;
        }
    }
}
