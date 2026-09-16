package io.koraframework.database.jdbc.telemetry.impl;

import io.koraframework.database.jdbc.telemetry.JdbcTransactionContext;
import org.jspecify.annotations.Nullable;

public final class NoopJdbcDatabaseMetricsFactory extends DefaultJdbcDatabaseMetricsFactory {

    public static final NoopJdbcDatabaseMetricsFactory INSTANCE = new NoopJdbcDatabaseMetricsFactory();

    private NoopJdbcDatabaseMetricsFactory() {}

    @Override
    public DefaultJdbcDatabaseMetrics create(DefaultJdbcDatabaseTelemetry.TelemetryContext context) {
        return NoopJdbcDatabaseMetrics.INSTANCE;
    }

    public static final class NoopJdbcDatabaseMetrics extends DefaultJdbcDatabaseMetrics {

        public static final NoopJdbcDatabaseMetrics INSTANCE = new NoopJdbcDatabaseMetrics();

        private NoopJdbcDatabaseMetrics() {
            super(DefaultJdbcDatabaseTelemetry.TelemetryContext.EMPTY);
        }

        @Override
        public void recordTransactionStart(JdbcTransactionContext transaction) {

        }

        @Override
        public void recordTransactionEnd(JdbcTransactionContext transaction, @Nullable Throwable error, long processingTimeNanos) {

        }
    }
}
