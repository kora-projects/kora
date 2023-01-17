package ru.tinkoff.kora.resilient.timeout;


import javax.annotation.Nonnull;

/**
 * Manages state of all {@link Timeouter} in system
 */
public interface TimeouterManager {

    @Nonnull
    Timeouter get(@Nonnull String name);
}
