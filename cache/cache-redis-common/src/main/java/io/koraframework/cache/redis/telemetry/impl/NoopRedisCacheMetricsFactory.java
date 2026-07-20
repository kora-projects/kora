package io.koraframework.cache.redis.telemetry.impl;

import io.koraframework.cache.redis.telemetry.RedisCacheTelemetry;
import org.jspecify.annotations.Nullable;

public final class NoopRedisCacheMetricsFactory extends DefaultRedisCacheMetricsFactory {

    public static final NoopRedisCacheMetricsFactory INSTANCE = new NoopRedisCacheMetricsFactory();

    private NoopRedisCacheMetricsFactory() {}

    @Override
    public DefaultRedisCacheMetrics create(DefaultRedisCacheTelemetry.TelemetryContext context) {
        return NoopRedisCacheMetrics.INSTANCE;
    }

    private static final class NoopRedisCacheMetrics extends DefaultRedisCacheMetrics {

        public static final NoopRedisCacheMetrics INSTANCE = new NoopRedisCacheMetrics();

        private NoopRedisCacheMetrics() {
            super(DefaultRedisCacheTelemetry.TelemetryContext.EMPTY);
        }

        @Override
        public void reportCommandTook(RedisCacheTelemetry.Operation operation, long startedRecordsHandleInNanos, @Nullable Throwable error) {
            // do nothing
        }

        @Override
        public void reportRatioChange(RedisCacheTelemetry.Operation operation, RatioType ratioType, int change) {
            // do nothing
        }
    }
}
