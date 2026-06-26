package io.koraframework.scheduling.common.telemetry.impl;

import io.koraframework.scheduling.common.telemetry.SchedulingObservation;
import io.opentelemetry.api.trace.Span;

public final class NoopSchedulingObservation implements SchedulingObservation {

    public static final NoopSchedulingObservation INSTANCE = new NoopSchedulingObservation();

    @Override
    public void observeRun() {

    }

    @Override
    public Span span() {
        return Span.getInvalid();
    }

    @Override
    public void end() {

    }

    @Override
    public void observeError(Throwable e) {

    }
}
