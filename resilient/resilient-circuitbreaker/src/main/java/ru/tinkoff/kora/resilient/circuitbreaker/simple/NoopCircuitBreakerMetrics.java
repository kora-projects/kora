package ru.tinkoff.kora.resilient.circuitbreaker.simple;

import ru.tinkoff.kora.resilient.circuitbreaker.CircuitBreaker;
import ru.tinkoff.kora.resilient.circuitbreaker.telemetry.CircuitBreakerMetrics;

import javax.annotation.Nonnull;

final class NoopCircuitBreakerMetrics implements CircuitBreakerMetrics {

    @Override
    public void recordState(@Nonnull String name, @Nonnull CircuitBreaker.State newState) {
        // do nothing
    }
}
