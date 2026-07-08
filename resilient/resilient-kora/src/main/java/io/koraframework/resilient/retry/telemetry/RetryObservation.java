package io.koraframework.resilient.retry.telemetry;

import io.koraframework.common.telemetry.Observation;

public interface RetryObservation extends Observation {

    enum StopReason {
        EXHAUSTED_ATTEMPTS,
        EXHAUSTED_BUDGET
    }

    void recordAttempt(long delayInNanos);

    void recordExhausted(StopReason reason, int totalAttempts);
}
