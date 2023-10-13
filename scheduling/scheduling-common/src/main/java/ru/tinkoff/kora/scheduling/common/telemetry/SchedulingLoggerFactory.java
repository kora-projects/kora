package ru.tinkoff.kora.scheduling.common.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

public interface SchedulingLoggerFactory {
    @Nullable
    SchedulingLogger get(TelemetryConfig.LogConfig logging, Class<?> jobClass, String jobMethod);
}
