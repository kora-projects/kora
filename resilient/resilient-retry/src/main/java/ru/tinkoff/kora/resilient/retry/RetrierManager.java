package ru.tinkoff.kora.resilient.retry;

import javax.annotation.Nonnull;

public interface RetrierManager {

    @Nonnull
    Retrier get(@Nonnull String name);
}
