package io.koraframework.database.common.telemetry.impl;

import io.koraframework.database.common.telemetry.DatabaseObservation;
import io.opentelemetry.api.trace.Span;

public final class NoopDatabaseObservation implements DatabaseObservation {

    public static final DatabaseObservation INSTANCE = new NoopDatabaseObservation();

    private NoopDatabaseObservation() {}

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
