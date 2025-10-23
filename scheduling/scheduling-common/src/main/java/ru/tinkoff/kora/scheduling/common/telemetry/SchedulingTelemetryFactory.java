package ru.tinkoff.kora.scheduling.common.telemetry;

import jakarta.annotation.Nullable;

public interface SchedulingTelemetryFactory {
    SchedulingTelemetry get(@Nullable JobTelemetryConfig jobTelemetryConfig, Class<?> jobClass, String jobMethod);
}
