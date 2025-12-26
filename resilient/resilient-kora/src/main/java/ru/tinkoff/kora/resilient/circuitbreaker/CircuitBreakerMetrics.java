package ru.tinkoff.kora.resilient.circuitbreaker;

/**
 * Records circuit breaker metrics
 */
public interface CircuitBreakerMetrics {

    enum CallAcquireStatus {
        PERMITTED,
        REJECTED,
        DISABLED
    }

    default void recordCallAcquire(String name, CallAcquireStatus callStatus) {

    }

    void recordState(String name, CircuitBreaker.State state);
}
