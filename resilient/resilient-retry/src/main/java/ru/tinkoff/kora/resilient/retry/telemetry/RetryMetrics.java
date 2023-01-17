package ru.tinkoff.kora.resilient.retry.telemetry;

import javax.annotation.Nonnull;

public interface RetryMetrics {

    void recordAttempt(@Nonnull String name, long delayInNanos);

    void recordExhaustedAttempts(@Nonnull String name, int totalAttempts);
}
