package io.koraframework.resilient.bulkhead.telemetry.impl;

public final class NoopBulkheadMetricsFactory extends DefaultBulkheadMetricsFactory {

    public static final NoopBulkheadMetricsFactory INSTANCE = new NoopBulkheadMetricsFactory();

    private NoopBulkheadMetricsFactory() {}

    @Override
    public DefaultBulkheadMetrics create(DefaultBulkheadTelemetry.TelemetryContext context) {
        return NoopBulkheadMetrics.INSTANCE;
    }

    public static final class NoopBulkheadMetrics extends DefaultBulkheadMetrics {

        public static final NoopBulkheadMetrics INSTANCE = new NoopBulkheadMetrics();

        private NoopBulkheadMetrics() {
            super(DefaultBulkheadTelemetry.TelemetryContext.EMPTY);
        }

        @Override
        public void recordAcquire(boolean acquired) {}

        @Override
        public void register(io.koraframework.resilient.bulkhead.Bulkhead bulkhead) {}

        @Override
        public void recordDuration(long durationNanos) {}

        @Override
        public void recordQueueWait(long durationNanos) {}
    }
}
