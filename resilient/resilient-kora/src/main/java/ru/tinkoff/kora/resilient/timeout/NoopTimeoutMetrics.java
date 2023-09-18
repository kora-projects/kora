package ru.tinkoff.kora.resilient.timeout;

import jakarta.annotation.Nonnull;

final class NoopTimeoutMetrics implements TimeoutMetrics {

    @Override
    public void recordTimeout(@Nonnull String name, long timeoutInNanos) {
        // do nothing
    }
}
