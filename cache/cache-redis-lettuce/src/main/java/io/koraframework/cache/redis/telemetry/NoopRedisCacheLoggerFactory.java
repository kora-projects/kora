package io.koraframework.cache.redis.telemetry;

import org.jspecify.annotations.Nullable;
import org.slf4j.helpers.NOPLogger;

import java.util.Collection;

public final class NoopRedisCacheLoggerFactory extends DefaultRedisCacheLoggerFactory {

    public static final NoopRedisCacheLoggerFactory INSTANCE = new NoopRedisCacheLoggerFactory();

    private NoopRedisCacheLoggerFactory() {}

    @Override
    public DefaultRedisCacheLogger create(DefaultRedisCacheTelemetry.TelemetryContext context) {
        return NoopRedisCacheLogger.INSTANCE;
    }

    public static final class NoopRedisCacheLogger extends DefaultRedisCacheLogger {

        public static final NoopRedisCacheLogger INSTANCE = new NoopRedisCacheLogger();

        private NoopRedisCacheLogger() {
            super(NOPLogger.NOP_LOGGER, DefaultRedisCacheTelemetry.TelemetryContext.EMPTY);
        }

        @Override
        public void logStart(String operation, Object key) {

        }

        @Override
        public void logStart(String operation, Collection<?> keys) {

        }

        @Override
        public void logEnd(String operation, long startedInNanos, @Nullable Throwable error) {

        }

        @Override
        public void logEnd(String operation, long startedInNanos, int retrieved, int missed) {

        }
    }
}
