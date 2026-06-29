package io.koraframework.resilient.fallback.telemetry.impl;

import org.jspecify.annotations.Nullable;
import org.slf4j.helpers.NOPLogger;

public final class NoopFallbackLoggerFactory extends DefaultFallbackLoggerFactory {

    public static final NoopFallbackLoggerFactory INSTANCE = new NoopFallbackLoggerFactory();

    private NoopFallbackLoggerFactory() {}

    @Override
    public DefaultFallbackLogger create(DefaultFallbackTelemetry.TelemetryContext context) {
        return NoopFallbackLogger.INSTANCE;
    }

    public static final class NoopFallbackLogger extends DefaultFallbackLogger {

        public static final NoopFallbackLogger INSTANCE = new NoopFallbackLogger();

        private NoopFallbackLogger() {
            super(NOPLogger.NOP_LOGGER, DefaultFallbackTelemetry.TelemetryContext.EMPTY);
        }

        @Override
        public void logStartFallback() {}

        @Override
        public void logExecute(long processingTimeNanos, Throwable throwable, @Nullable Throwable exception) {}
    }
}
