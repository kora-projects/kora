package io.koraframework.cache.redis.telemetry;

import org.jspecify.annotations.Nullable;

public final class NoopRedisCacheMetricsFactory extends DefaultRedisCacheMetricsFactory {

    public static final NoopRedisCacheMetricsFactory INSTANCE = new NoopRedisCacheMetricsFactory();

    private NoopRedisCacheMetricsFactory() {}

    @Override
    public DefaultRedisCacheMetrics create(DefaultRedisCacheTelemetry.TelemetryContext context) {
        return NoopRedisCacheMetrics.INSTANCE;
    }

    private static final class NoopRedisCacheMetrics extends DefaultRedisCacheMetrics {

        public static final NoopRedisCacheMetrics INSTANCE = new NoopRedisCacheMetrics(null);

        private NoopRedisCacheMetrics(DefaultRedisCacheTelemetry.TelemetryContext context) {
            super(context);
        }

        @Override
        public void reportCommandTook(String operation, long startedRecordsHandleInNanos, @Nullable Throwable error) {
            // do nothing
        }

        @Override
        public void reportRatioChange(String operation, RatioType ratioType, int change) {
            // do nothing
        }
    }
}
