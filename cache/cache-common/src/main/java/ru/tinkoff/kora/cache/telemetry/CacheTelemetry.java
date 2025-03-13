package ru.tinkoff.kora.cache.telemetry;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

public interface CacheTelemetry {

    interface CacheTelemetryContext {

        void recordSuccess(@Nullable Object valueFromCache);

        void recordFailure(@Nullable Throwable throwable);
    }

    CacheTelemetryContext get(@Nonnull String operationName);
}
