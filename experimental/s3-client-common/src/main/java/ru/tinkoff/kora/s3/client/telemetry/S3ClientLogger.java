package ru.tinkoff.kora.s3.client.telemetry;

import jakarta.annotation.Nullable;

public interface S3ClientLogger {

    void logRequest(@Nullable String operation,
                    @Nullable String bucket,
                    String method,
                    String path,
                    @Nullable Long contentLength);

    void logResponse(@Nullable String operation,
                     @Nullable String bucket,
                     String method,
                     String path,
                     int statusCode,
                     long processingTime,
                     @Nullable Throwable exception);
}
