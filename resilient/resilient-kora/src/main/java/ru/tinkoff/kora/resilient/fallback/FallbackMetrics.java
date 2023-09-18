package ru.tinkoff.kora.resilient.fallback;

import jakarta.annotation.Nonnull;

public interface FallbackMetrics {

    void recordExecute(@Nonnull String name, @Nonnull Throwable throwable);
}
