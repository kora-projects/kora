package io.koraframework.resilient.circuitbreaker.telemetry;

public interface CircuitBreakerTelemetryFactory {

    CircuitBreakerTelemetry get(String name, CircuitBreakerTelemetryConfig config);
}
