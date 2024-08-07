package ru.tinkoff.kora.s3.client.telemetry;

import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import ru.tinkoff.kora.logging.common.arg.StructuredArgument;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CancellationException;

public class DefaultS3ClientLogger implements S3ClientLogger {

    private final String clientName;
    private final Logger requestLogger;
    private final Logger responseLogger;

    public DefaultS3ClientLogger(String clientName, Logger requestLogger, Logger responseLogger) {
        this.clientName = clientName;
        this.requestLogger = requestLogger;
        this.responseLogger = responseLogger;
    }

    @Override
    public void logRequest(@Nullable String operation,
                           @Nullable String bucket,
                           String method,
                           String path,
                           @Nullable Long contentLength) {
        if (requestLogger.isInfoEnabled()) {
            var marker = StructuredArgument.marker("s3Request", gen -> {
                gen.writeStartObject();
                if (operation != null) {
                    gen.writeStringField("operation", operation);
                }
                if (bucket != null) {
                    gen.writeStringField("bucket", bucket);
                }
                gen.writeStringField("method", method);
                gen.writeStringField("path", path);
                if (contentLength != null) {
                    gen.writeNumberField("contentLength", contentLength);
                }
                gen.writeEndObject();
            });

            this.requestLogger.info(marker, "S3 Client starting operation for {} {}", method, path);
        }
    }

    @Override
    public void logResponse(@Nullable String operation,
                            @Nullable String bucket,
                            String method,
                            String path,
                            int statusCode,
                            long processingTime,
                            @Nullable Throwable exception) {
        if (responseLogger.isInfoEnabled()) {
            var exceptionTypeString = exception != null
                ? exception.getClass().getCanonicalName()
                : CancellationException.class.getCanonicalName();

            var marker = StructuredArgument.marker("s3Response", gen -> {
                gen.writeStartObject();
                if (operation != null) {
                    gen.writeStringField("operation", operation);
                }
                if (bucket != null) {
                    gen.writeStringField("bucket", bucket);
                }
                gen.writeStringField("method", method);
                gen.writeStringField("path", path);
                gen.writeNumberField("statusCode", statusCode);
                gen.writeNumberField("processingTime", processingTime / 1_000_000);
                if (exceptionTypeString != null) {
                    gen.writeStringField("exceptionType", exceptionTypeString);
                }
                gen.writeEndObject();
            });

            responseLogger.info(marker, "S3 Client finished operation with statusCode {} for {} {} in {}",
                statusCode, method, path, Duration.ofNanos(processingTime).truncatedTo(ChronoUnit.MILLIS));
        }
    }
}
