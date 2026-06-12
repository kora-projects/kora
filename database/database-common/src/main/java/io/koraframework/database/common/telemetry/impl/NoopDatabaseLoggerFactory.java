package io.koraframework.database.common.telemetry.impl;

import io.koraframework.database.common.QueryContext;
import org.jspecify.annotations.Nullable;
import org.slf4j.helpers.NOPLogger;

public final class NoopDatabaseLoggerFactory extends DefaultDatabaseLoggerFactory {

    public static final NoopDatabaseLoggerFactory INSTANCE = new NoopDatabaseLoggerFactory();

    private NoopDatabaseLoggerFactory() {}

    @Override
    public DefaultDatabaseLogger create(DefaultDatabaseTelemetry.TelemetryContext context) {
        return NoopDatabaseLogger.INSTANCE;
    }

    public static final class NoopDatabaseLogger extends DefaultDatabaseLogger {

        public static final NoopDatabaseLogger INSTANCE = new NoopDatabaseLogger();

        private NoopDatabaseLogger() {
            super(NOPLogger.NOP_LOGGER, DefaultDatabaseTelemetry.TelemetryContext.EMPTY);
        }

        @Override
        public void logQueryBegin(QueryContext query) {

        }

        @Override
        public void logQueryEnd(QueryContext query, @Nullable Throwable error, long processingTimeNanos) {

        }
    }
}
