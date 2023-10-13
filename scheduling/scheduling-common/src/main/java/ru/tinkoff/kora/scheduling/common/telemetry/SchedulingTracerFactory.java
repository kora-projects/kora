package ru.tinkoff.kora.scheduling.common.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

public interface SchedulingTracerFactory {
    @Nullable
    SchedulingTracer get(TelemetryConfig.TracingConfig tracing, Class<?> jobClass, String jobMethod);
}
