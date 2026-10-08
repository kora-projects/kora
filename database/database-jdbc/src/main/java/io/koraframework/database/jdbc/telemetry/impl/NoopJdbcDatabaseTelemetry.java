package io.koraframework.database.jdbc.telemetry.impl;

import io.koraframework.database.jdbc.telemetry.JdbcDatabaseTelemetry;
import io.koraframework.database.jdbc.telemetry.JdbcTransactionContext;
import io.koraframework.database.jdbc.telemetry.JdbcTransactionObservation;

public final class NoopJdbcDatabaseTelemetry implements JdbcDatabaseTelemetry {

    public static final NoopJdbcDatabaseTelemetry INSTANCE = new NoopJdbcDatabaseTelemetry();

    private NoopJdbcDatabaseTelemetry() {}

    @Override
    public JdbcTransactionObservation observeTransaction(JdbcTransactionContext transaction) {
        return NoopJdbcTransactionObservation.INSTANCE;
    }
}
