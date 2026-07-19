package io.koraframework.resilient.circuitbreaker;

final class KoraCircuitBreakerPredicate implements CircuitBreakerPredicate {

    @Override
    public boolean test(Throwable throwable) {
        return true;
    }
}
