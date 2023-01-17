package ru.tinkoff.kora.resilient.timeout.telemetry;

import javax.annotation.Nonnull;

public interface TimeoutMetrics {

    void recordTimeout(@Nonnull String name, long timeoutInNanos);
}
