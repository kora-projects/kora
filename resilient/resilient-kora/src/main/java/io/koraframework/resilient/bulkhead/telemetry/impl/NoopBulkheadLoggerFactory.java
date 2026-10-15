package io.koraframework.resilient.bulkhead.telemetry.impl;

import org.jspecify.annotations.Nullable;
import org.slf4j.helpers.NOPLogger;

public final class NoopBulkheadLoggerFactory extends DefaultBulkheadLoggerFactory {

    public static final NoopBulkheadLoggerFactory INSTANCE = new NoopBulkheadLoggerFactory();

    private NoopBulkheadLoggerFactory() {}

    @Override
    public DefaultBulkheadLogger create(DefaultBulkheadTelemetry.TelemetryContext context) {
        return NoopBulkheadLogger.INSTANCE;
    }

    public static final class NoopBulkheadLogger extends DefaultBulkheadLogger {

        public static final NoopBulkheadLogger INSTANCE = new NoopBulkheadLogger();

        private NoopBulkheadLogger() {
            super(NOPLogger.NOP_LOGGER, DefaultBulkheadTelemetry.TelemetryContext.EMPTY);
        }

        @Override
        public void logStartAcquire() {}

        @Override
        public void logAcquire(boolean acquired, long processingTimeNanos, @Nullable Throwable exception) {}
    }
}
