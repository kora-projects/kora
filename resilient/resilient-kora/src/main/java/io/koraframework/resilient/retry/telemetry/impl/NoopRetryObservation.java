package io.koraframework.resilient.retry.telemetry.impl;

import io.koraframework.resilient.retry.telemetry.RetryObservation;
import io.koraframework.resilient.retry.telemetry.RetryObservation.StopReason;
import io.opentelemetry.api.trace.Span;

public final class NoopRetryObservation implements RetryObservation {

    public static final NoopRetryObservation INSTANCE = new NoopRetryObservation();

    private NoopRetryObservation() {}

    @Override
    public void recordAttempt(long delayInNanos) {}

    @Override
    public void recordExhausted(StopReason reason, int totalAttempts) {}

    @Override
    public Span span() {
        return Span.getInvalid();
    }

    @Override
    public void end() {}

    @Override
    public void observeError(Throwable e) {}
}
