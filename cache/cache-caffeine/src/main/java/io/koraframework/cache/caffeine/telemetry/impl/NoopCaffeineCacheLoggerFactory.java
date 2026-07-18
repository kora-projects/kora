package io.koraframework.cache.caffeine.telemetry.impl;

import org.slf4j.helpers.NOPLogger;

public final class NoopCaffeineCacheLoggerFactory extends DefaultCaffeineCacheLoggerFactory {

    public static final NoopCaffeineCacheLoggerFactory INSTANCE = new NoopCaffeineCacheLoggerFactory();

    private NoopCaffeineCacheLoggerFactory() {}

    @Override
    public DefaultCaffeineCacheLogger create(DefaultCaffeineCacheTelemetry.TelemetryContext context) {
        return new NoopCaffeineCacheLogger();
    }

    private static final class NoopCaffeineCacheLogger extends DefaultCaffeineCacheLogger {

        private NoopCaffeineCacheLogger() {
            super(NOPLogger.NOP_LOGGER, DefaultCaffeineCacheTelemetry.TelemetryContext.EMPTY);
        }
    }
}
