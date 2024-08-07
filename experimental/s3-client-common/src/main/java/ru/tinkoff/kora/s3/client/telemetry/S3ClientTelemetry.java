package ru.tinkoff.kora.s3.client.telemetry;

import jakarta.annotation.Nullable;

import java.net.URI;

public interface S3ClientTelemetry {

    interface S3ClientTelemetryContext {

        void prepared(String method, String path, URI uri, String host, int port, @Nullable Long contentLength);

        default void close(int statusCode) {
            close(statusCode, null);
        }

        void close(int statusCode, @Nullable Throwable exception);
    }

    S3ClientTelemetryContext get(@Nullable String operation,
                                 @Nullable String bucket);
}
