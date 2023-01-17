package ru.tinkoff.kora.resilient.fallback.telemetry;

import javax.annotation.Nonnull;

public interface FallbackMetrics {

    void recordExecute(@Nonnull String name, @Nonnull Throwable throwable);
}
