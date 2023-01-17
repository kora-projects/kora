package ru.tinkoff.kora.resilient.fallback;


import javax.annotation.Nonnull;

public interface FallbackerManager {

    @Nonnull
    Fallbacker get(@Nonnull String name);
}
