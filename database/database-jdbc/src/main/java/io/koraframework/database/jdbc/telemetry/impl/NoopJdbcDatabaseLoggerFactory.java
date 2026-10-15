package io.koraframework.database.jdbc.telemetry.impl;

import io.koraframework.database.jdbc.telemetry.JdbcTransactionContext;
import org.jspecify.annotations.Nullable;
import org.slf4j.helpers.NOPLogger;

public final class NoopJdbcDatabaseLoggerFactory extends DefaultJdbcDatabaseLoggerFactory {

    public static final NoopJdbcDatabaseLoggerFactory INSTANCE = new NoopJdbcDatabaseLoggerFactory();

    private NoopJdbcDatabaseLoggerFactory() {}

    @Override
    public DefaultJdbcDatabaseLogger create(DefaultJdbcDatabaseTelemetry.TelemetryContext context) {
        return NoopJdbcDatabaseLogger.INSTANCE;
    }

    public static final class NoopJdbcDatabaseLogger extends DefaultJdbcDatabaseLogger {

        public static final NoopJdbcDatabaseLogger INSTANCE = new NoopJdbcDatabaseLogger();

        private NoopJdbcDatabaseLogger() {
            super(NOPLogger.NOP_LOGGER, DefaultJdbcDatabaseTelemetry.TelemetryContext.EMPTY);
        }

        @Override
        public void logTransactionBegin(JdbcTransactionContext transaction) {

        }

        @Override
        public void logTransactionEnd(JdbcTransactionContext transaction, boolean committed, @Nullable Throwable error, long processingTimeNanos) {

        }
    }
}
