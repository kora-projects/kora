package ru.tinkoff.kora.cache.telemetry;

import org.jspecify.annotations.Nullable;

public interface CacheMetrics {

    void recordSuccess(CacheTelemetryOperation operation, long durationInNanos, @Nullable Object valueFromCache);

    void recordFailure(CacheTelemetryOperation operation, long durationInNanos, @Nullable Throwable throwable);
}
