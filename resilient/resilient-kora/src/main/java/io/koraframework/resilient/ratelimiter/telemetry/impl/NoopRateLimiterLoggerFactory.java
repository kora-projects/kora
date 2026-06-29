package io.koraframework.resilient.ratelimiter.telemetry.impl;

import org.jspecify.annotations.Nullable;
import org.slf4j.helpers.NOPLogger;

public final class NoopRateLimiterLoggerFactory extends DefaultRateLimiterLoggerFactory {

    public static final NoopRateLimiterLoggerFactory INSTANCE = new NoopRateLimiterLoggerFactory();

    private NoopRateLimiterLoggerFactory() {}

    @Override
    public DefaultRateLimiterLogger create(DefaultRateLimiterTelemetry.TelemetryContext context) {
        return NoopRateLimiterLogger.INSTANCE;
    }

    public static final class NoopRateLimiterLogger extends DefaultRateLimiterLogger {

        public static final NoopRateLimiterLogger INSTANCE = new NoopRateLimiterLogger();

        private NoopRateLimiterLogger() {
            super(NOPLogger.NOP_LOGGER, DefaultRateLimiterTelemetry.TelemetryContext.EMPTY);
        }

        @Override
        public void logStartAcquire() {}

        @Override
        public void logAcquire(boolean acquired, long processingTimeNanos, @Nullable Throwable exception) {}
    }
}
