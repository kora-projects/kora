package ru.tinkoff.kora.resilient.circuitbreaker.simple;

import ru.tinkoff.kora.resilient.circuitbreaker.CircuitBreakerFailurePredicate;

import javax.annotation.Nonnull;

final class SimpleCircuitBreakerFailurePredicate implements CircuitBreakerFailurePredicate {

    @Nonnull
    @Override
    public String name() {
        return SimpleCircuitBreakerFailurePredicate.class.getCanonicalName();
    }

    @Override
    public boolean test(@Nonnull Throwable throwable) {
        return true;
    }
}
