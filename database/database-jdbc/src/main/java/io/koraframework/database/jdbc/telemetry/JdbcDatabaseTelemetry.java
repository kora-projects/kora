package io.koraframework.database.jdbc.telemetry;

public interface JdbcDatabaseTelemetry {

    JdbcTransactionObservation observeTransaction(JdbcTransactionContext transaction);
}
