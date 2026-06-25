package io.koraframework.s3.client.kora.telemetry.impl;

import org.jspecify.annotations.Nullable;
import org.slf4j.helpers.NOPLogger;

public final class NoopS3ClientLoggerFactory extends DefaultS3ClientLoggerFactory {

    public static final NoopS3ClientLoggerFactory INSTANCE = new NoopS3ClientLoggerFactory();

    private NoopS3ClientLoggerFactory() {}

    @Override
    public DefaultS3ClientLogger create(DefaultS3ClientTelemetry.TelemetryContext context) {
        return NoopS3ClientLogger.INSTANCE;
    }

    public static final class NoopS3ClientLogger extends DefaultS3ClientLogger {

        public static final NoopS3ClientLogger INSTANCE = new NoopS3ClientLogger();

        private NoopS3ClientLogger() {
            super(DefaultS3ClientTelemetry.TelemetryContext.EMPTY, NOPLogger.NOP_LOGGER);
        }

        @Override
        public void logStart(String operation, String bucket) {

        }

        @Override
        public void logEnd(String operation, String bucket, @Nullable Throwable error, long processingTimeNanos) {

        }
    }
}
