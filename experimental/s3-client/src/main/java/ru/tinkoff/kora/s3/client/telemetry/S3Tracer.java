package ru.tinkoff.kora.s3.client.telemetry;

import jakarta.annotation.Nullable;

public interface S3Tracer {

    interface S3Span {
        void setError(Throwable t);

        void close();
    }

    S3Span createSpan(String operation, String bucket, @Nullable String key, @Nullable Long contentLength);
}
