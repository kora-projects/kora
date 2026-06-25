package io.koraframework.s3.client.kora.telemetry.impl;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultS3ClientLoggerFactory {

    public static final DefaultS3ClientLoggerFactory INSTANCE = new DefaultS3ClientLoggerFactory();

    public DefaultS3ClientLogger create(DefaultS3ClientTelemetry.TelemetryContext context) {
        var logger = LoggerFactory.getLogger(context.clientCanonicalName());
        return new DefaultS3ClientLogger(context, logger);
    }

    public static class DefaultS3ClientLogger {

        protected final DefaultS3ClientTelemetry.TelemetryContext context;
        protected final Logger logger;

        public DefaultS3ClientLogger(DefaultS3ClientTelemetry.TelemetryContext context, Logger logger) {
            this.context = context;
            this.logger = logger;
        }

        public void logStart(String operation, String bucket) {
            if (!this.logger.isDebugEnabled()) {
                return;
            }
            this.logger.atDebug()
                .addKeyValue("clientConfigPath", this.context.clientConfigPath())
                .addKeyValue("operation", operation)
                .addKeyValue("bucket", bucket)
                .log("S3Client request started");
        }

        public void logEnd(String operation, String bucket, @Nullable Throwable error, long processingTimeNanos) {
            if (error == null) {
                if (!this.logger.isDebugEnabled()) {
                    return;
                }
                this.logger.atDebug()
                    .addKeyValue("clientConfigPath", this.context.clientConfigPath())
                    .addKeyValue("operation", operation)
                    .addKeyValue("bucket", bucket)
                    .addKeyValue("processingTime", processingTimeNanos / 1_000_000)
                    .log("S3Client response received");
            } else {
                if (!this.logger.isWarnEnabled()) {
                    return;
                }
                var errorType = error.getClass().getCanonicalName();
                var errorMessage = error.getMessage();
                var log = this.logger.atWarn()
                    .addKeyValue("clientConfigPath", this.context.clientConfigPath())
                    .addKeyValue("operation", operation)
                    .addKeyValue("bucket", bucket)
                    .addKeyValue("processingTime", processingTimeNanos / 1_000_000);
                if (errorType != null) {
                    log.addKeyValue("exceptionType", errorType);
                }
                if (errorMessage != null) {
                    log.addKeyValue("exceptionMessage", errorMessage);
                }
                log.log("S3Client error received");
            }
        }
    }
}
