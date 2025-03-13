package ru.tinkoff.kora.cache.telemetry;

import jakarta.annotation.Nonnull;

public interface CacheTelemetryArgs {

    @Nonnull
    String cacheName();

    @Nonnull
    String origin();
}
