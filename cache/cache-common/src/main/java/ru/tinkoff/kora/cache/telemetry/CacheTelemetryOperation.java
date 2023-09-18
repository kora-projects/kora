package ru.tinkoff.kora.cache.telemetry;

import jakarta.annotation.Nonnull;

public interface CacheTelemetryOperation {
    @Nonnull
    String name();

    @Nonnull
    String cacheName();

    @Nonnull
    String origin();
}
