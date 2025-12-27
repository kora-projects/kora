package ru.tinkoff.kora.cache.telemetry;

import org.jspecify.annotations.Nullable;

public interface CacheTracer {

    interface CacheSpan {

        void recordSuccess();

        void recordFailure(@Nullable Throwable throwable);
    }

    CacheSpan trace(CacheTelemetryOperation operation);
}
