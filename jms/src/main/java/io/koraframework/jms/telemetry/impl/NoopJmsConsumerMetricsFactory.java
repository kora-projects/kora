package io.koraframework.jms.telemetry.impl;

import org.jspecify.annotations.Nullable;

public final class NoopJmsConsumerMetricsFactory extends DefaultJmsConsumerMetricsFactory {

    public static final NoopJmsConsumerMetricsFactory INSTANCE = new NoopJmsConsumerMetricsFactory();

    private NoopJmsConsumerMetricsFactory() {}

    @Override
    public DefaultJmsConsumerMetrics create(DefaultJmsConsumerTelemetry.TelemetryContext context) {
        return NoopJmsConsumerMetrics.INSTANCE;
    }

    public static final class NoopJmsConsumerMetrics extends DefaultJmsConsumerMetrics {

        public static final NoopJmsConsumerMetrics INSTANCE = new NoopJmsConsumerMetrics();

        private NoopJmsConsumerMetrics() {
            super(DefaultJmsConsumerTelemetry.TelemetryContext.EMPTY);
        }

        @Override
        public void recordEnd(String destination, @Nullable Throwable exception, long processingTimeNanos) {}
    }
}
