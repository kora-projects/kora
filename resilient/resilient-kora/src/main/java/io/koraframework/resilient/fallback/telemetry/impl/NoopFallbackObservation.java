package io.koraframework.resilient.fallback.telemetry.impl;

import io.koraframework.resilient.fallback.telemetry.FallbackObservation;
import io.opentelemetry.api.trace.Span;

public final class NoopFallbackObservation implements FallbackObservation {

    public static final NoopFallbackObservation INSTANCE = new NoopFallbackObservation();

    private NoopFallbackObservation() {}

    @Override
    public void recordExecute(Throwable throwable) {}

    @Override
    public Span span() {
        return Span.getInvalid();
    }

    @Override
    public void end() {}

    @Override
    public void observeError(Throwable e) {}
}
