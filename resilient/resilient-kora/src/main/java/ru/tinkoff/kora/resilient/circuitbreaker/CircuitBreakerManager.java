package ru.tinkoff.kora.resilient.circuitbreaker;


import jakarta.annotation.Nonnull;

/**
 * Manages state of all {@link CircuitBreaker} in system
 */
public interface CircuitBreakerManager {

    @Nonnull
    CircuitBreaker get(@Nonnull String name);
}
