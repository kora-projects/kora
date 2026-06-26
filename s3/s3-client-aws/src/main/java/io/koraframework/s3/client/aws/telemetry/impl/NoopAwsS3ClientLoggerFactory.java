package io.koraframework.s3.client.aws.telemetry.impl;

import org.jspecify.annotations.Nullable;
import org.slf4j.helpers.NOPLogger;

public final class NoopAwsS3ClientLoggerFactory extends DefaultAwsS3ClientLoggerFactory {

    public static final NoopAwsS3ClientLoggerFactory INSTANCE = new NoopAwsS3ClientLoggerFactory();

    private NoopAwsS3ClientLoggerFactory() {}

    @Override
    public DefaultAwsS3ClientLogger create(DefaultAwsS3ClientTelemetry.TelemetryContext context) {
        return NoopAwsS3ClientLogger.INSTANCE;
    }

    public static final class NoopAwsS3ClientLogger extends DefaultAwsS3ClientLogger {

        public static final NoopAwsS3ClientLogger INSTANCE = new NoopAwsS3ClientLogger();

        private NoopAwsS3ClientLogger() {
            super(DefaultAwsS3ClientTelemetry.TelemetryContext.EMPTY, NOPLogger.NOP_LOGGER);
        }

        @Override
        public void logStart(String operation, String bucket) {

        }

        @Override
        public void logEnd(String operation, String bucket, @Nullable Throwable error, long processingTimeNanos) {

        }
    }
}
