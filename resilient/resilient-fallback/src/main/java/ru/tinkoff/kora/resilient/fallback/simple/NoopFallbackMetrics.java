package ru.tinkoff.kora.resilient.fallback.simple;

import ru.tinkoff.kora.resilient.fallback.telemetry.FallbackMetrics;

import javax.annotation.Nonnull;

final class NoopFallbackMetrics implements FallbackMetrics {

    static final NoopFallbackMetrics INSTANCE = new NoopFallbackMetrics();

    @Override
    public void recordExecute(@Nonnull String name, @Nonnull Throwable throwable) {
        // do nothing
    }
}
