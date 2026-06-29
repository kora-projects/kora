package io.koraframework.resilient.fallback.telemetry;

public interface FallbackTelemetryFactory {

    FallbackTelemetry get(String name, FallbackTelemetryConfig config);
}
