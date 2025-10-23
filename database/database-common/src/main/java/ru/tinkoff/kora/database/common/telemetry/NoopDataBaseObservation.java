package ru.tinkoff.kora.database.common.telemetry;

import io.opentelemetry.api.trace.Span;

public class NoopDataBaseObservation implements DataBaseObservation {
    @Override
    public void observeConnection() {
    }

    @Override
    public void observeStatement() {
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
