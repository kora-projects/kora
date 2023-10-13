package ru.tinkoff.kora.database.common.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

public interface DataBaseMetricWriterFactory {
    @Nullable
    DataBaseMetricWriter get(TelemetryConfig.MetricsConfig metrics, String poolName);
}
