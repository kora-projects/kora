package io.koraframework.scheduling.common.telemetry.impl;

import org.jspecify.annotations.Nullable;

public final class NoopSchedulingMetricsFactory extends DefaultSchedulingMetricsFactory {

    public static final NoopSchedulingMetricsFactory INSTANCE = new NoopSchedulingMetricsFactory();

    private NoopSchedulingMetricsFactory() {}

    @Override
    public DefaultSchedulingMetrics create(DefaultSchedulingTelemetry.TelemetryContext context) {
        return NoopSchedulingMetrics.INSTANCE;
    }

    public static final class NoopSchedulingMetrics extends DefaultSchedulingMetrics {

        public static final NoopSchedulingMetrics INSTANCE = new NoopSchedulingMetrics();

        private NoopSchedulingMetrics() {
            super(null);
        }

        @Override
        public void record(@Nullable Throwable throwable, long durationInNanos) {

        }
    }
}
