package io.koraframework.resilient.circuitbreaker.telemetry.impl;

import io.koraframework.resilient.circuitbreaker.*;
import io.koraframework.resilient.circuitbreaker.telemetry.*;
import io.opentelemetry.api.trace.Span;

public final class NoopCircuitBreakerObservation implements CircuitBreakerObservation {

    public static final NoopCircuitBreakerObservation INSTANCE = new NoopCircuitBreakerObservation();

    private NoopCircuitBreakerObservation() {}

    @Override
    public void recordCallAcquire(CircuitBreaker.State state, CallAcquireStatus callStatus) {}

    @Override
    public void recordStateChange(CircuitBreaker.State previousState, CircuitBreaker.State newState) {}

    @Override
    public Span span() {
        return Span.getInvalid();
    }

    @Override
    public void end() {}

    @Override
    public void observeError(Throwable e) {}
}
