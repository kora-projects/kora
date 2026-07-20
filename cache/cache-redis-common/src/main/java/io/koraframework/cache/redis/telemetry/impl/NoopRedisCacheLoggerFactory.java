package io.koraframework.cache.redis.telemetry.impl;

import io.koraframework.cache.redis.telemetry.RedisCacheTelemetry;
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
        public void logStart(RedisCacheTelemetry.Operation operation, Object key) {

        }

        @Override
        public void logStart(RedisCacheTelemetry.Operation operation, Collection<?> keys) {

        }

        @Override
        public void logEnd(RedisCacheTelemetry.Operation operation, long startedInNanos, @Nullable Throwable error) {

        }

        @Override
        public void logEnd(RedisCacheTelemetry.Operation operation, long startedInNanos, int retrieved, int missed) {

        }
    }
}
