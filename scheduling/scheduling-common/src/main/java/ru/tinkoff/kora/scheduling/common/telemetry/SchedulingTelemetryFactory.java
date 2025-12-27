package ru.tinkoff.kora.scheduling.common.telemetry;

import org.jspecify.annotations.Nullable;

public interface SchedulingTelemetryFactory {
    SchedulingTelemetry get(@Nullable JobTelemetryConfig jobTelemetryConfig, Class<?> jobClass, String jobMethod);
}
