package ru.tinkoff.kora.cache;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.cache.telemetry.*;
import ru.tinkoff.kora.common.DefaultComponent;

public interface CacheModule {

    @DefaultComponent
    default CacheLoggerFactory defaultCacheLoggerFactory() {
        return new Sl4fjCacheLoggerFactory();
    }

    @DefaultComponent
    default CacheTelemetryFactory defaultCacheTelemetryFactory(@Nullable CacheLoggerFactory loggerFactory,
                                                               @Nullable CacheMetricsFactory metricsFactory,
                                                               @Nullable CacheTracerFactory tracerFactory) {
        return new DefaultCacheTelemetryFactory(loggerFactory, metricsFactory, tracerFactory);
    }
}
