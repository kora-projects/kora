package ru.tinkoff.kora.s3.client.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.s3.client.S3Exception;

public interface S3ClientLogger {

    void logRequest(String method,
                    String bucket,
                    @Nullable String key,
                    @Nullable Long contentLength);

    void logResponse(String method,
                     String bucket,
                     @Nullable String key,
                     int statusCode,
                     long processingTimeNanos,
                     @Nullable S3Exception exception);
}
