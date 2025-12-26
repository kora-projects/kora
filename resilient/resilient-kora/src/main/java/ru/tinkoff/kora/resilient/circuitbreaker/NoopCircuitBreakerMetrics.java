package ru.tinkoff.kora.resilient.circuitbreaker;


final class NoopCircuitBreakerMetrics implements CircuitBreakerMetrics {

    @Override
    public void recordCallAcquire(String name, CircuitBreakerMetrics.CallAcquireStatus callStatus) {

    }

    @Override
    public void recordState(String name, CircuitBreaker.State newState) {
        // do nothing
    }
}
