package io.koraframework.resilient.retry.telemetry;

import io.koraframework.common.telemetry.Observation;

public interface RetryObservation extends Observation {

    void recordAttempt(long delayInNanos);

    void recordExhaustedAttempts(int totalAttempts);
}
