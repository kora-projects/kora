package ru.tinkoff.kora.s3.client.telemetry;

import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.logging.common.arg.StructuredArgument;

public class DefaultS3Logger implements S3Logger {

    private final Logger requestLogger;
    private final Logger responseLogger;

    public DefaultS3Logger(Class<?> client) {
        this.requestLogger = LoggerFactory.getLogger(client.getCanonicalName() + ".request");
        this.responseLogger = LoggerFactory.getLogger(client.getCanonicalName() + ".response");
    }

    @Override
    public void logRequest(String method, String bucket, @Nullable String key, @Nullable Long contentLength) {
        if (!requestLogger.isInfoEnabled()) {
            return;
        }
        var marker = StructuredArgument.marker("s3Request", gen -> {
            gen.writeStartObject();
            gen.writeStringField("method", method);
            gen.writeStringField("bucket", bucket);
            if (key != null) {
                gen.writeStringField("key", key);
            }
            if (contentLength != null) {
                gen.writeNumberField("contentLength", contentLength);
            }
            gen.writeEndObject();
        });

        if (key == null) {
            this.requestLogger.info(marker, "S3 Client starting operation for {} {}", method, bucket);
        } else {
            this.requestLogger.info(marker, "S3 Client starting operation for {} {}/{}", method, bucket, key);
        }
    }

    @Override
    public void logResponse(String operation, String bucket, @Nullable String key, long processingTimeNanos, @Nullable Throwable exception) {

    }
}
