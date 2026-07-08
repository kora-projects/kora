package io.koraframework.resilient.retry.telemetry.impl;

import io.koraframework.resilient.retry.telemetry.RetryObservation;

public final class NoopRetryMetricsFactory extends DefaultRetryMetricsFactory {

    public static final NoopRetryMetricsFactory INSTANCE = new NoopRetryMetricsFactory();

    private NoopRetryMetricsFactory() {}

    @Override
    public DefaultRetryMetrics create(DefaultRetryTelemetry.TelemetryContext context) {
        return NoopRetryMetrics.INSTANCE;
    }

    public static final class NoopRetryMetrics extends DefaultRetryMetrics {

        public static final NoopRetryMetrics INSTANCE = new NoopRetryMetrics();

        private NoopRetryMetrics() {
            super(DefaultRetryTelemetry.TelemetryContext.EMPTY);
        }

        @Override
        public void recordAttempt(long delayInNanos) {}

        @Override
        public void recordExhausted(int totalAttempts, RetryObservation.StopReason reason) {}
    }
}
