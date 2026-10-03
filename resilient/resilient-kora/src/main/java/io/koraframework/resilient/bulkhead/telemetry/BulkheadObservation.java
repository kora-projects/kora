package io.koraframework.resilient.bulkhead.telemetry;

import io.koraframework.common.telemetry.Observation;

public interface BulkheadObservation extends Observation {

    void recordAcquire(boolean acquired);

    default void recordQueueWait(long durationNanos) {}
}
