package ru.tinkoff.kora.s3.client.telemetry;

import jakarta.annotation.Nullable;

public interface S3ClientMetrics {

    void record(@Nullable String operation,
                @Nullable String bucket,
                String method,
                int statusCode,
                long processingTimeNanos,
                @Nullable Throwable exception);
}
