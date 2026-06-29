package io.koraframework.resilient.timeout.telemetry;

public interface TimeoutTelemetryFactory {

    TimeoutTelemetry get(String name, TimeoutTelemetryConfig config);
}
