package ru.tinkoff.kora.resilient.timeout;

import jakarta.annotation.Nonnull;

public interface TimeoutMetrics {

    void recordTimeout(@Nonnull String name, long timeoutInNanos);
}
