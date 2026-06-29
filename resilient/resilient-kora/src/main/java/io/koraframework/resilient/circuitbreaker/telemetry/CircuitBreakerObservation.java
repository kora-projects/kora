package io.koraframework.resilient.circuitbreaker.telemetry;

import io.koraframework.common.telemetry.Observation;
import io.koraframework.resilient.circuitbreaker.CircuitBreaker;

public interface CircuitBreakerObservation extends Observation {

    enum CallAcquireStatus {
        PERMITTED,
        REJECTED,
        DISABLED,
    }

    void recordCallAcquire(CircuitBreaker.State state, CallAcquireStatus callStatus);

    void recordStateChange(CircuitBreaker.State previousState, CircuitBreaker.State newState);
}
