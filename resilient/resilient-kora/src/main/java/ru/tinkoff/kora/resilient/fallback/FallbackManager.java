package ru.tinkoff.kora.resilient.fallback;


import javax.annotation.Nonnull;

public interface FallbackManager {

    @Nonnull
    Fallback get(@Nonnull String name);
}
