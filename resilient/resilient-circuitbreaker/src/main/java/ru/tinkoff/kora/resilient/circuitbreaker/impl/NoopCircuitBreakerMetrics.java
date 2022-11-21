package ru.tinkoff.kora.resilient.circuitbreaker.impl;

import ru.tinkoff.kora.resilient.circuitbreaker.CircuitBreaker;
import ru.tinkoff.kora.resilient.circuitbreaker.telemetry.CircuitBreakerMetrics;

import javax.annotation.Nonnull;

final class NoopCircuitBreakerMetrics implements CircuitBreakerMetrics {

    static final NoopCircuitBreakerMetrics INSTANCE = new NoopCircuitBreakerMetrics();

    @Override
    public void recordState(@Nonnull String name, @Nonnull CircuitBreaker.State newState) {

    }
}
