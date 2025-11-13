package ru.tinkoff.kora.scheduling.common.telemetry;

import io.opentelemetry.api.trace.Span;

public class NoopSchedulingObservation implements SchedulingObservation {
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
