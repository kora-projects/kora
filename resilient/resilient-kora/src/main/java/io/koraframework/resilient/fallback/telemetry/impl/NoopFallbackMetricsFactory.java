package io.koraframework.resilient.fallback.telemetry.impl;

public final class NoopFallbackMetricsFactory extends DefaultFallbackMetricsFactory {

    public static final NoopFallbackMetricsFactory INSTANCE = new NoopFallbackMetricsFactory();

    private NoopFallbackMetricsFactory() {}

    @Override
    public DefaultFallbackMetrics create(DefaultFallbackTelemetry.TelemetryContext context) {
        return NoopFallbackMetrics.INSTANCE;
    }

    public static final class NoopFallbackMetrics extends DefaultFallbackMetrics {

        public static final NoopFallbackMetrics INSTANCE = new NoopFallbackMetrics();

        private NoopFallbackMetrics() {
            super(DefaultFallbackTelemetry.TelemetryContext.EMPTY);
        }

        @Override
        public void recordExecute(Throwable throwable) {}
    }
}
