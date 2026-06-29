package io.koraframework.resilient.timeout.telemetry.impl;

public final class NoopTimeoutMetricsFactory extends DefaultTimeoutMetricsFactory {

    public static final NoopTimeoutMetricsFactory INSTANCE = new NoopTimeoutMetricsFactory();

    private NoopTimeoutMetricsFactory() {}

    @Override
    public DefaultTimeoutMetrics create(DefaultTimeoutTelemetry.TelemetryContext context) {
        return NoopTimeoutMetrics.INSTANCE;
    }

    public static final class NoopTimeoutMetrics extends DefaultTimeoutMetrics {

        public static final NoopTimeoutMetrics INSTANCE = new NoopTimeoutMetrics();

        private NoopTimeoutMetrics() {
            super(DefaultTimeoutTelemetry.TelemetryContext.EMPTY);
        }

        @Override
        public void recordTimeout(long timeoutInNanos) {}
    }
}
