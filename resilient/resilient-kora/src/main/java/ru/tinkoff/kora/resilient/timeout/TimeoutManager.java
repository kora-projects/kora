package ru.tinkoff.kora.resilient.timeout;


import jakarta.annotation.Nonnull;

/**
 * Manages state of all {@link Timeout} in system
 */
public interface TimeoutManager {

    @Nonnull
    Timeout get(@Nonnull String name);
}
