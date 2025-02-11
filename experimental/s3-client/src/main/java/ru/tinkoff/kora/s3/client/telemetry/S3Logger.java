package ru.tinkoff.kora.s3.client.telemetry;

import jakarta.annotation.Nullable;

public interface S3Logger {


    void logRequest(String operation, String bucket, @Nullable String key, @Nullable Long contentLength);

    void logResponse(String operation, String bucket, @Nullable String key, long processingTimeNanos, @Nullable Throwable exception);
}
