package ru.tinkoff.kora.s3.client.telemetry;

import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.logging.common.arg.StructuredArgument;
import ru.tinkoff.kora.s3.client.S3Exception;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

public class DefaultS3ClientLogger implements S3ClientLogger {

    private final Logger requestLogger;
    private final Logger responseLogger;

    public DefaultS3ClientLogger(Class<?> client) {
        this.requestLogger = LoggerFactory.getLogger(client.getCanonicalName() + ".request");
        this.responseLogger = LoggerFactory.getLogger(client.getCanonicalName() + ".response");
    }

    @Override
    public void logRequest(String method,
                           String bucket,
                           @Nullable String key,
                           @Nullable Long contentLength) {
        if (requestLogger.isInfoEnabled()) {
            var marker = StructuredArgument.marker("s3Request", gen -> {
                gen.writeStartObject();
                gen.writeStringField("method", method);
                gen.writeStringField("bucket", bucket);
                if(key != null) {
                    gen.writeStringField("key", key);
                }
                if (contentLength != null) {
                    gen.writeNumberField("contentLength", contentLength);
                }
                gen.writeEndObject();
            });

            if(key == null) {
                this.requestLogger.info(marker, "S3 Client starting operation for {} {}", method, bucket);
            } else {
                this.requestLogger.info(marker, "S3 Client starting operation for {} {}/{}", method, bucket, key);
            }
        }
    }

    @Override
    public void logResponse(String method,
                            String bucket,
                            @Nullable String key,
                            int statusCode,
                            long processingTimeNanos,
                            @Nullable S3Exception exception) {
        if (responseLogger.isInfoEnabled()) {
            var marker = StructuredArgument.marker("s3Response", gen -> {
                gen.writeStartObject();
                gen.writeStringField("method", method);
                gen.writeStringField("bucket", bucket);
                if(key != null) {
                    gen.writeStringField("key", key);
                }
                gen.writeNumberField("statusCode", statusCode);
                gen.writeNumberField("processingTime", processingTimeNanos / 1_000_000);
                if (exception != null) {
                    gen.writeStringField("errorCode", exception.getErrorCode());
                    final String exType = (exception.getCause() == null)
                        ? exception.getClass().getCanonicalName()
                        : exception.getCause().getClass().getCanonicalName();
                    gen.writeStringField("exceptionType", exType);
                }
                gen.writeEndObject();
            });

            if(key == null) {
                this.responseLogger.info(marker, "S3 Client finished operation with statusCode {} for {} {} in {}",
                    statusCode, method, bucket, Duration.ofNanos(processingTimeNanos).truncatedTo(ChronoUnit.MILLIS));
            } else {
                this.responseLogger.info(marker, "S3 Client finished operation with statusCode {} for {} {}/{} in {}",
                    statusCode, method, bucket, key, Duration.ofNanos(processingTimeNanos).truncatedTo(ChronoUnit.MILLIS));
            }
        }
    }
}
