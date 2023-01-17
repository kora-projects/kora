package ru.tinkoff.kora.resilient.timeout.simple;

import ru.tinkoff.kora.resilient.timeout.telemetry.TimeoutMetrics;

import javax.annotation.Nonnull;

final class NoopTimeoutMetrics implements TimeoutMetrics {

    @Override
    public void recordTimeout(@Nonnull String name, long timeoutInNanos) {
        // do nothing
    }
}
