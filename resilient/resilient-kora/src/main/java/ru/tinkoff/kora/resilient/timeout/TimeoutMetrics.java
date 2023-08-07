package ru.tinkoff.kora.resilient.timeout;

import javax.annotation.Nonnull;

public interface TimeoutMetrics {

    void recordTimeout(@Nonnull String name, long timeoutInNanos);
}
