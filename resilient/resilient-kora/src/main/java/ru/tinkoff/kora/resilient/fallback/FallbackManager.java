package ru.tinkoff.kora.resilient.fallback;


import jakarta.annotation.Nonnull;

public interface FallbackManager {

    @Nonnull
    Fallback get(@Nonnull String name);
}
