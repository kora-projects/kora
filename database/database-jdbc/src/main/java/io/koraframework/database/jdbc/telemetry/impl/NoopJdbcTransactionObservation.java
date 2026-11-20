package io.koraframework.database.jdbc.telemetry.impl;

import io.koraframework.database.jdbc.telemetry.JdbcTransactionObservation;
import io.opentelemetry.api.trace.Span;

public final class NoopJdbcTransactionObservation implements JdbcTransactionObservation {

    public static final JdbcTransactionObservation INSTANCE = new NoopJdbcTransactionObservation();

    private NoopJdbcTransactionObservation() {}

    @Override
    public void observeCommit() {

    }

    @Override
    public void observeRollback() {

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
