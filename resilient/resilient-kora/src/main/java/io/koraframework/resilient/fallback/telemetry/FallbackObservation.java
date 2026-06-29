package io.koraframework.resilient.fallback.telemetry;

import io.koraframework.common.telemetry.Observation;

public interface FallbackObservation extends Observation {

    void recordExecute(Throwable throwable);
}
