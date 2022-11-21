package ru.tinkoff.kora.cache.telemetry;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public interface CacheTracer {

    interface CacheSpan {

        void recordSuccess();

        void recordFailure(@Nullable Throwable throwable);
    }

    CacheSpan trace(@Nonnull CacheTelemetry.Operation operation);
}
