package io.koraframework.s3.client.kora.telemetry.impl;

import io.koraframework.logging.common.arg.StructuredArgumentWriter;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultS3ClientLoggerFactory {

    public static final DefaultS3ClientLoggerFactory INSTANCE = new DefaultS3ClientLoggerFactory();

    public DefaultS3ClientLogger create(DefaultS3ClientTelemetry.TelemetryContext context) {
        var logger = LoggerFactory.getLogger("io.koraframework.s3.client.kora");
        return new DefaultS3ClientLogger(logger);
    }

    public static class DefaultS3ClientLogger {

        protected final Logger logger;

        public DefaultS3ClientLogger(Logger logger) {
            this.logger = logger;
        }

        public void logStart(String operation, String bucket) {
            if (!this.logger.isDebugEnabled()) {
                return;
            }
            var arg = (StructuredArgumentWriter) gen -> {
                gen.writeStartObject();
                gen.writeStringProperty("operation", operation);
                gen.writeStringProperty("bucket", bucket);
                gen.writeEndObject();
            };
            this.logger.atDebug()
                .addKeyValue("s3Request", arg)
                .log("S3Client request started");
        }

        public void logEnd(String operation, String bucket, @Nullable Throwable error, long processingTimeNanos) {
            if (error == null) {
                if (!this.logger.isDebugEnabled()) {
                    return;
                }
                var arg = (StructuredArgumentWriter) gen -> {
                    gen.writeStartObject();
                    gen.writeStringProperty("operation", operation);
                    gen.writeStringProperty("bucket", bucket);
                    gen.writeNumberProperty("processingTime", processingTimeNanos / 1_000_000);
                    gen.writeEndObject();
                };
                this.logger.atDebug()
                    .addKeyValue("s3Response", arg)
                    .log("S3Client response received");
            } else {
                if (!this.logger.isWarnEnabled()) {
                    return;
                }
                var errorType = error.getClass().getCanonicalName();
                var errorMessage = error.getMessage();
                var arg = (StructuredArgumentWriter) gen -> {
                    gen.writeStartObject();
                    gen.writeStringProperty("operation", operation);
                    gen.writeStringProperty("bucket", bucket);
                    gen.writeNumberProperty("processingTime", processingTimeNanos / 1_000_000);
                    if (errorType != null) {
                        gen.writeStringProperty("exceptionType", errorType);
                    }
                    if (errorMessage != null) {
                        gen.writeStringProperty("exceptionMessage", errorMessage);
                    }
                    gen.writeEndObject();
                };
                this.logger.atWarn()
                    .addKeyValue("s3Response", arg)
                    .log("S3Client error received");
            }
        }
    }
}
