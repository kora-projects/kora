package io.koraframework.resilient.ratelimiter.telemetry.impl;

import io.koraframework.resilient.ratelimiter.telemetry.RateLimiterObservation;
import io.opentelemetry.api.trace.Span;

public final class NoopRateLimiterObservation implements RateLimiterObservation {

    public static final NoopRateLimiterObservation INSTANCE = new NoopRateLimiterObservation();

    private NoopRateLimiterObservation() {}

    @Override
    public void recordAcquire(boolean acquired) {}

    @Override
    public Span span() {
        return Span.getInvalid();
    }

    @Override
    public void end() {}

    @Override
    public void observeError(Throwable e) {}
}
