package ru.tinkoff.kora.cache.telemetry;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

public interface CacheLogger {

    void logStart(@Nonnull CacheTelemetryOperation operation);

    void logSuccess(@Nonnull CacheTelemetryOperation operation, long durationInNanos, @Nullable Object valueFromCache);

    void logFailure(@Nonnull CacheTelemetryOperation operation, long durationInNanos, @Nullable Throwable exception);
}
