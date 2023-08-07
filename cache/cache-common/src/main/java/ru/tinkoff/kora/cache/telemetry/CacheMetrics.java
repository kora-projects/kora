package ru.tinkoff.kora.cache.telemetry;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public interface CacheMetrics {

    void recordSuccess(@Nonnull CacheTelemetryOperation operation, long durationInNanos, @Nullable Object valueFromCache);

    void recordFailure(@Nonnull CacheTelemetryOperation operation, long durationInNanos, @Nullable Throwable throwable);
}
