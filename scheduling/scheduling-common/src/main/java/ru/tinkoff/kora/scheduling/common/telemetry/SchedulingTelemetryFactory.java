package ru.tinkoff.kora.scheduling.common.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

public interface SchedulingTelemetryFactory {
    SchedulingTelemetry get(@Nullable TelemetryConfig jobTelemetryConfig, Class<?> jobClass, String jobMethod);
}
