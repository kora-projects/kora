package io.koraframework.resilient.bulkhead.telemetry;

public interface BulkheadTelemetryFactory {

    BulkheadTelemetry get(String name, BulkheadTelemetryConfig config);
}
