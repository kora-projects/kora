package io.koraframework.cache.caffeine.telemetry.impl;

public final class NoopCaffeineCacheMetricsFactory extends DefaultCaffeineCacheMetricsFactory {

    public static final NoopCaffeineCacheMetricsFactory INSTANCE = new NoopCaffeineCacheMetricsFactory();

    private NoopCaffeineCacheMetricsFactory() {}

    @Override
    public DefaultCaffeineCacheMetrics create(DefaultCaffeineCacheTelemetry.TelemetryContext context) {
        return new DefaultCaffeineCacheMetrics(DefaultCaffeineCacheTelemetry.TelemetryContext.EMPTY);
    }
}
