package io.koraframework.resilient.bulkhead.telemetry.impl;

import io.koraframework.resilient.bulkhead.telemetry.BulkheadObservation;
import io.opentelemetry.api.trace.Span;

public final class NoopBulkheadObservation implements BulkheadObservation {

    public static final NoopBulkheadObservation INSTANCE = new NoopBulkheadObservation();

    private NoopBulkheadObservation() {}

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
