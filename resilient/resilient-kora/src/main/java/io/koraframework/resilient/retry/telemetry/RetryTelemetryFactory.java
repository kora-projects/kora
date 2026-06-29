package io.koraframework.resilient.retry.telemetry;

public interface RetryTelemetryFactory {

    RetryTelemetry get(String name, RetryTelemetryConfig config);
}
