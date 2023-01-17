package ru.tinkoff.kora.resilient.retry.simple;

import ru.tinkoff.kora.resilient.retry.telemetry.RetryMetrics;

import javax.annotation.Nonnull;

final class NoopRetryMetrics implements RetryMetrics {

    @Override
    public void recordAttempt(@Nonnull String name, long delayInNanos) {
        // do nothing
    }

    @Override
    public void recordExhaustedAttempts(@Nonnull String name, int totalAttempts) {
        // do nothing
    }
}
