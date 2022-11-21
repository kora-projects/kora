package ru.tinkoff.kora.resilient.circuitbreaker.telemetry;

import ru.tinkoff.kora.resilient.circuitbreaker.CircuitBreaker;

import javax.annotation.Nonnull;

/**
 * Records circuit breaker metrics
 */
public interface CircuitBreakerMetrics {

    void recordState(@Nonnull String name, @Nonnull CircuitBreaker.State state);
}
