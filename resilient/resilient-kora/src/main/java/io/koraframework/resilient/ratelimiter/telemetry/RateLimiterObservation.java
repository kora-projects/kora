package io.koraframework.resilient.ratelimiter.telemetry;

import io.koraframework.common.telemetry.Observation;

public interface RateLimiterObservation extends Observation {

    void recordAcquire(boolean acquired);
}
