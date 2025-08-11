package ru.tinkoff.kora.resilient.circuitbreaker;

import jakarta.annotation.Nonnull;

/**
 * Records circuit breaker metrics
 */
public interface CircuitBreakerMetrics {

    enum CallAcquireStatus {
        PERMITTED,
        REJECTED,
        DISABLED
    }

    default void recordCallAcquire(@Nonnull String name, @Nonnull CallAcquireStatus callStatus) {

    }

    void recordState(@Nonnull String name, @Nonnull CircuitBreaker.State state);
}
