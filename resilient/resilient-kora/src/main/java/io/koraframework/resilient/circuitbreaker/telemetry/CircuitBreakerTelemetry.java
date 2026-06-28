package io.koraframework.resilient.circuitbreaker.telemetry;

public interface CircuitBreakerTelemetry {

    CircuitBreakerObservation observe();
}
