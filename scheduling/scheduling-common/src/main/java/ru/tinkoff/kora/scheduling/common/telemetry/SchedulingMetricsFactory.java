package ru.tinkoff.kora.scheduling.common.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

public interface SchedulingMetricsFactory {
    @Nullable
    SchedulingMetrics get(TelemetryConfig.MetricsConfig metrics, Class<?> jobClass, String jobMethod);
}
