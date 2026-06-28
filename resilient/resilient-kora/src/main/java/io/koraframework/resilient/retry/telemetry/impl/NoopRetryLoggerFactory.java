package io.koraframework.resilient.retry.telemetry.impl;

import org.jspecify.annotations.Nullable;
import org.slf4j.helpers.NOPLogger;

public final class NoopRetryLoggerFactory extends DefaultRetryLoggerFactory {

    public static final NoopRetryLoggerFactory INSTANCE = new NoopRetryLoggerFactory();

    private NoopRetryLoggerFactory() {}

    @Override
    public DefaultRetryLogger create(DefaultRetryTelemetry.TelemetryContext context) {
        return NoopRetryLogger.INSTANCE;
    }

    public static final class NoopRetryLogger extends DefaultRetryLogger {

        public static final NoopRetryLogger INSTANCE = new NoopRetryLogger();

        private NoopRetryLogger() {
            super(NOPLogger.NOP_LOGGER, DefaultRetryTelemetry.TelemetryContext.EMPTY);
        }

        @Override
        public void logStartRetry() {}

        @Override
        public void logRetry(int attempts, boolean exhausted, long lastDelayInNanos, long processingTimeNanos, @Nullable Throwable exception) {}
    }
}
