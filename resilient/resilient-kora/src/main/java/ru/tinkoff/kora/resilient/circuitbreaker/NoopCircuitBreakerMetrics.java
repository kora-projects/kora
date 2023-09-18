package ru.tinkoff.kora.resilient.circuitbreaker;


import jakarta.annotation.Nonnull;

final class NoopCircuitBreakerMetrics implements CircuitBreakerMetrics {

    @Override
    public void recordState(@Nonnull String name, @Nonnull CircuitBreaker.State newState) {
        // do nothing
    }
}
