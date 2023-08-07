package ru.tinkoff.kora.resilient.timeout;

import javax.annotation.Nonnull;

final class NoopTimeoutMetrics implements TimeoutMetrics {

    @Override
    public void recordTimeout(@Nonnull String name, long timeoutInNanos) {
        // do nothing
    }
}
