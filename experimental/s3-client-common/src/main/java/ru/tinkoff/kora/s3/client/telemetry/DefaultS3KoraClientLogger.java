package ru.tinkoff.kora.s3.client.telemetry;

import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.logging.common.arg.StructuredArgument;
import ru.tinkoff.kora.s3.client.S3Exception;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

public class DefaultS3KoraClientLogger implements S3KoraClientLogger {

    private final Logger requestLogger;
    private final Logger responseLogger;

    public DefaultS3KoraClientLogger(Class<?> clientImpl) {
        this.requestLogger = LoggerFactory.getLogger(clientImpl.getCanonicalName() + ".request");
        this.responseLogger = LoggerFactory.getLogger(clientImpl.getCanonicalName() + ".response");
    }

    @Override
    public void logRequest(String operation,
                           String bucket,
                           @Nullable String key,
                           @Nullable Long contentLength) {
        if (requestLogger.isInfoEnabled()) {
            var marker = StructuredArgument.marker("s3Operation", gen -> {
                gen.writeStartObject();
                gen.writeStringProperty("operation", operation);
                gen.writeStringProperty("bucket", bucket);
                if (key != null) {
                    gen.writeStringProperty("key", key);
                }
                if (contentLength != null) {
                    gen.writeNumberProperty("contentLength", contentLength);
                }
                gen.writeEndObject();
            });

            this.requestLogger.info(marker, "S3 Kora Client starting operation {} for bucket {}", operation, bucket);
        }
    }

    @Override
    public void logResponse(String operation,
                            String bucket,
                            @Nullable String key,
                            long processingTimeNanos,
                            @Nullable S3Exception exception) {
        if (responseLogger.isInfoEnabled()) {
            var marker = StructuredArgument.marker("s3Operation", gen -> {
                gen.writeStartObject();
                gen.writeStringProperty("operation", operation);
                gen.writeStringProperty("bucket", bucket);
                if (key != null) {
                    gen.writeStringProperty("key", key);
                }
                gen.writeStringProperty("status", (exception == null) ? "success" : "failure");
                gen.writeNumberProperty("processingTime", processingTimeNanos / 1_000_000);
                if (exception != null) {
                    gen.writeStringProperty("errorCode", exception.getErrorCode());
                    final String exType = (exception.getCause() == null)
                        ? exception.getClass().getCanonicalName()
                        : exception.getCause().getClass().getCanonicalName();
                    gen.writeStringProperty("exceptionType", exType);
                }
                gen.writeEndObject();
            });

            responseLogger.info(marker, "S3 Kora Client finished operation {} for bucket {} in {}",
                operation, bucket, Duration.ofNanos(processingTimeNanos).truncatedTo(ChronoUnit.MILLIS));
        }
    }
}
