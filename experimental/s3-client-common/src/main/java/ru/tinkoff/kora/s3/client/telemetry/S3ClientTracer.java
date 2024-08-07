package ru.tinkoff.kora.s3.client.telemetry;

import jakarta.annotation.Nullable;

import java.net.URI;

public interface S3ClientTracer {

    interface S3ClientSpan {

        void prepared(String method, String path, URI uri, String host, int port, @Nullable Long contentLength);

        void close(int statusCode, @Nullable Throwable exception);
    }

    S3ClientSpan createSpan(@Nullable String operation,
                            @Nullable String bucket);
}
