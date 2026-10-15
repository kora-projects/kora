package io.koraframework.resilient.circuitbreaker.telemetry.impl;

import io.koraframework.resilient.circuitbreaker.telemetry.CircuitBreakerObservation;
import io.koraframework.resilient.circuitbreaker.telemetry.CircuitBreakerTelemetry;

public final class NoopCircuitBreakerTelemetry implements CircuitBreakerTelemetry {

    public static final NoopCircuitBreakerTelemetry INSTANCE = new NoopCircuitBreakerTelemetry();

    private NoopCircuitBreakerTelemetry() {}

    @Override
    public CircuitBreakerObservation observe() {
        return NoopCircuitBreakerObservation.INSTANCE;
    }
}
