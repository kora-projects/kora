package io.koraframework.resilient.timeout.telemetry;

import io.koraframework.common.telemetry.Observation;

public interface TimeoutObservation extends Observation {

    void recordTimeout(long timeoutInNanos);
}
