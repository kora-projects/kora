package io.koraframework.resilient.bulkhead.telemetry.impl;

import io.koraframework.resilient.bulkhead.telemetry.BulkheadObservation;
import io.koraframework.resilient.bulkhead.telemetry.BulkheadTelemetry;

public final class NoopBulkheadTelemetry implements BulkheadTelemetry {

    public static final NoopBulkheadTelemetry INSTANCE = new NoopBulkheadTelemetry();

    private NoopBulkheadTelemetry() {}

    @Override
    public BulkheadObservation observe() {
        return NoopBulkheadObservation.INSTANCE;
    }
}
