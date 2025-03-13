package ru.tinkoff.kora.cache.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

public interface CacheLoggerFactory {

    @Nullable
    CacheLogger get(TelemetryConfig.LogConfig logging, CacheTelemetryArgs args);
}
