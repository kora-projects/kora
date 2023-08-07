package ru.tinkoff.kora.resilient.retry;

import javax.annotation.Nonnull;

public interface RetryManager {

    @Nonnull
    Retry get(@Nonnull String name);
}
