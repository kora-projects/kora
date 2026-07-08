package io.koraframework.resilient.circuitbreaker.telemetry;

import io.koraframework.common.telemetry.Observation;
import io.koraframework.resilient.circuitbreaker.CircuitBreaker;

public interface CircuitBreakerObservation extends Observation {

    enum CallAcquireStatus {
        PERMITTED,
        REJECTED,
        DISABLED,
    }

    enum CallResult {
        SUCCESS,
        FAILURE,
        IGNORED_FAILURE,
        FALLBACK
    }

    void recordCallAcquire(CircuitBreaker.State state, CallAcquireStatus callStatus);

    void recordCallResult(CircuitBreaker.State state, CallResult callResult);

    void recordStateChange(CircuitBreaker.State previousState, CircuitBreaker.State newState);
}
