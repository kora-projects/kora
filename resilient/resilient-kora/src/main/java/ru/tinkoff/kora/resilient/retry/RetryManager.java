package ru.tinkoff.kora.resilient.retry;

import jakarta.annotation.Nonnull;

public interface RetryManager {

    @Nonnull
    Retry get(@Nonnull String name);
}
