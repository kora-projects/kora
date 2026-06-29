package io.koraframework.resilient.ratelimiter.telemetry.impl;

public final class NoopRateLimiterMetricsFactory extends DefaultRateLimiterMetricsFactory {

    public static final NoopRateLimiterMetricsFactory INSTANCE = new NoopRateLimiterMetricsFactory();

    private NoopRateLimiterMetricsFactory() {}

    @Override
    public DefaultRateLimiterMetrics create(DefaultRateLimiterTelemetry.TelemetryContext context) {
        return NoopRateLimiterMetrics.INSTANCE;
    }

    public static final class NoopRateLimiterMetrics extends DefaultRateLimiterMetrics {

        public static final NoopRateLimiterMetrics INSTANCE = new NoopRateLimiterMetrics();

        private NoopRateLimiterMetrics() {
            super(DefaultRateLimiterTelemetry.TelemetryContext.EMPTY);
        }

        @Override
        public void recordAcquire(boolean acquired) {}
    }
}
