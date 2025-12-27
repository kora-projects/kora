package ru.tinkoff.kora.resilient.circuitbreaker;

final class KoraCircuitBreakerPredicate implements CircuitBreakerPredicate {

    @Override
    public String name() {
        return KoraCircuitBreakerPredicate.class.getCanonicalName();
    }

    @Override
    public boolean test(Throwable throwable) {
        return true;
    }
}
