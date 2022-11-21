package ru.tinkoff.kora.resilient.circuitbreaker.impl;

import ru.tinkoff.kora.resilient.circuitbreaker.CircuitBreakerFailurePredicate;

import javax.annotation.Nonnull;

final class FastCircuitBreakerFailurePredicate implements CircuitBreakerFailurePredicate {

    @Nonnull
    @Override
    public String name() {
        return FastCircuitBreakerFailurePredicate.class.getCanonicalName();
    }

    @Override
    public boolean test(@Nonnull Throwable throwable) {
        return true;
    }
}
