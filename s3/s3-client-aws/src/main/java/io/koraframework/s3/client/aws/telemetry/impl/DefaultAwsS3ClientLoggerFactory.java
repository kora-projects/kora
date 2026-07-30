package io.koraframework.s3.client.aws.telemetry.impl;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultAwsS3ClientLoggerFactory {

    public static final DefaultAwsS3ClientLoggerFactory INSTANCE = new DefaultAwsS3ClientLoggerFactory();

    public DefaultAwsS3ClientLogger create(DefaultAwsS3ClientTelemetry.TelemetryContext context) {
        var logger = LoggerFactory.getLogger(context.clientCanonicalName());
        return new DefaultAwsS3ClientLogger(context, logger);
    }

    public static class DefaultAwsS3ClientLogger {

        protected final DefaultAwsS3ClientTelemetry.TelemetryContext context;
        protected final Logger logger;

        public DefaultAwsS3ClientLogger(DefaultAwsS3ClientTelemetry.TelemetryContext context, Logger logger) {
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
                .log("AwsS3Client request started");
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
                    .log("AwsS3Client response received");
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
                log.log("AwsS3Client error received");
            }
        }
    }
}
