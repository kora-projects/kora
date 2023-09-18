package ru.tinkoff.kora.cache.telemetry;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

public interface CacheTracer {

    interface CacheSpan {

        void recordSuccess();

        void recordFailure(@Nullable Throwable throwable);
    }

    CacheSpan trace(@Nonnull CacheTelemetryOperation operation);
}
