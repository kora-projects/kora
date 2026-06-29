package io.koraframework.resilient.timeout.telemetry.impl;

import org.jspecify.annotations.Nullable;
import org.slf4j.helpers.NOPLogger;

public final class NoopTimeoutLoggerFactory extends DefaultTimeoutLoggerFactory {

    public static final NoopTimeoutLoggerFactory INSTANCE = new NoopTimeoutLoggerFactory();

    private NoopTimeoutLoggerFactory() {}

    @Override
    public DefaultTimeoutLogger create(DefaultTimeoutTelemetry.TelemetryContext context) {
        return NoopTimeoutLogger.INSTANCE;
    }

    public static final class NoopTimeoutLogger extends DefaultTimeoutLogger {

        public static final NoopTimeoutLogger INSTANCE = new NoopTimeoutLogger();

        private NoopTimeoutLogger() {
            super(NOPLogger.NOP_LOGGER, DefaultTimeoutTelemetry.TelemetryContext.EMPTY);
        }

        @Override
        public void logStartWaiting(long timeToWaitInNanos) {}

        @Override
        public void logTimeout(long timeoutInNanos, long processingTimeNanos, @Nullable Throwable exception) {}
    }
}
