package io.koraframework.resilient.bulkhead.telemetry;

import io.koraframework.resilient.bulkhead.Bulkhead;

public interface BulkheadTelemetry {

    BulkheadObservation observe();

    /**
     * Binds state gauges to the same limiter used for admission.
     */
    default void register(Bulkhead bulkhead) {}
}
