package ru.tinkoff.kora.cache.telemetry;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public interface CacheTelemetry {

    record Operation(@Nonnull Type type, @Nonnull String cacheName, @Nonnull String origin) {

        public enum Type {
            GET,
            PUT,
            INVALIDATE,
            INVALIDATE_ALL
        }
    }

    interface TelemetryContext {
        void startRecording();

        void recordSuccess();

        void recordSuccess(@Nullable Object valueFromCache);

        void recordFailure(@Nullable Throwable throwable);
    }

    @Nonnull
    TelemetryContext create(@Nonnull Operation.Type type, @Nonnull String cacheName, @Nonnull String origin);
}
