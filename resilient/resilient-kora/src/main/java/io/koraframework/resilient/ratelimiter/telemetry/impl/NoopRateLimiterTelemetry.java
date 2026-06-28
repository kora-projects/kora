package io.koraframework.resilient.ratelimiter.telemetry.impl;

import io.koraframework.resilient.ratelimiter.*;
import io.koraframework.resilient.ratelimiter.telemetry.*;

public final class NoopRateLimiterTelemetry implements RateLimiterTelemetry {

    public static final NoopRateLimiterTelemetry INSTANCE = new NoopRateLimiterTelemetry();

    private NoopRateLimiterTelemetry() {}

    @Override
    public RateLimiterObservation observe() {
        return NoopRateLimiterObservation.INSTANCE;
    }
}
