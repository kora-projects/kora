package ru.tinkoff.kora.cache.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

public interface CacheTracerFactory {

    @Nullable
    CacheTracer get(TelemetryConfig.TracingConfig tracing, CacheTelemetryArgs args);
}
