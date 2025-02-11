package ru.tinkoff.kora.s3.client.telemetry;

import jakarta.annotation.Nullable;

public interface S3Metrics {

    void record(String operation, String bucket, long processingTimeNanos, @Nullable Throwable exception);
}
