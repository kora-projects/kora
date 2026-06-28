package io.koraframework.resilient.timeout.telemetry.impl;

import io.koraframework.resilient.timeout.telemetry.TimeoutObservation;
import io.opentelemetry.api.trace.Span;

public final class NoopTimeoutObservation implements TimeoutObservation {

    public static final NoopTimeoutObservation INSTANCE = new NoopTimeoutObservation();

    private NoopTimeoutObservation() {}

    @Override
    public void recordTimeout(long timeoutInNanos) {}

    @Override
    public Span span() {
        return Span.getInvalid();
    }

    @Override
    public void end() {}

    @Override
    public void observeError(Throwable e) {}
}
