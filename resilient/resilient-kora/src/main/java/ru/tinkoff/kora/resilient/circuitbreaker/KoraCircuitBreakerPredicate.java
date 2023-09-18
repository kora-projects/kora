package ru.tinkoff.kora.resilient.circuitbreaker;

import jakarta.annotation.Nonnull;

final class KoraCircuitBreakerPredicate implements CircuitBreakerPredicate {

    @Nonnull
    @Override
    public String name() {
        return KoraCircuitBreakerPredicate.class.getCanonicalName();
    }

    @Override
    public boolean test(@Nonnull Throwable throwable) {
        return true;
    }
}
