package ru.tinkoff.kora.cache.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

public interface CacheMetricsFactory {

    @Nullable
    CacheMetrics get(TelemetryConfig.MetricsConfig config, CacheTelemetryArgs args);
}
