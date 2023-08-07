package ru.tinkoff.kora.resilient.circuitbreaker;

import javax.annotation.Nonnull;

/**
 * Records circuit breaker metrics
 */
public interface CircuitBreakerMetrics {

    void recordState(@Nonnull String name, @Nonnull CircuitBreaker.State state);
}
