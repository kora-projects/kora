package io.koraframework.s3.client.kora.telemetry.impl;

import org.jspecify.annotations.Nullable;

public final class NoopS3ClientMetricsFactory extends DefaultS3ClientMetricsFactory {

    public static final NoopS3ClientMetricsFactory INSTANCE = new NoopS3ClientMetricsFactory();

    private NoopS3ClientMetricsFactory() {}

    @Override
    public DefaultS3ClientMetrics create(DefaultS3ClientTelemetry.TelemetryContext context) {
        return NoopS3ClientMetrics.INSTANCE;
    }

    public static final class NoopS3ClientMetrics extends DefaultS3ClientMetrics {

        public static final NoopS3ClientMetrics INSTANCE = new NoopS3ClientMetrics();

        private NoopS3ClientMetrics() {
            super(DefaultS3ClientTelemetry.TelemetryContext.EMPTY);
        }

        @Override
        public void record(String operation, String bucket, @Nullable Throwable error, long startedRequestInNanos) {

        }
    }
}
