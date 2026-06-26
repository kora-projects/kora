package io.koraframework.s3.client.aws.telemetry.impl;

import org.jspecify.annotations.Nullable;

public final class NoopAwsS3ClientMetricsFactory extends DefaultAwsS3ClientMetricsFactory {

    public static final NoopAwsS3ClientMetricsFactory INSTANCE = new NoopAwsS3ClientMetricsFactory();

    private NoopAwsS3ClientMetricsFactory() {}

    @Override
    public DefaultAwsS3ClientMetrics create(DefaultAwsS3ClientTelemetry.TelemetryContext context) {
        return NoopAwsS3ClientMetrics.INSTANCE;
    }

    public static final class NoopAwsS3ClientMetrics extends DefaultAwsS3ClientMetrics {

        public static final NoopAwsS3ClientMetrics INSTANCE = new NoopAwsS3ClientMetrics();

        private NoopAwsS3ClientMetrics() {
            super(DefaultAwsS3ClientTelemetry.TelemetryContext.EMPTY);
        }

        @Override
        public void record(String operation, String bucket, @Nullable Throwable error, long startedRequestInNanos) {

        }
    }
}
