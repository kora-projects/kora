package ru.tinkoff.kora.s3.client.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.s3.client.S3Exception;

import java.net.URI;

public interface S3ClientTelemetry {

    interface S3ClientTelemetryContext {

        void prepared(String method,
                      String bucket,
                      @Nullable String key,
                      @Nullable Long contentLength);

        default void close(String method,
                           String bucket,
                           @Nullable String key,
                           int statusCode) {
            close(method, bucket, key, statusCode, null);
        }

        void close(String method,
                   String bucket,
                   @Nullable String key,
                   int statusCode,
                   @Nullable S3Exception exception);
    }

    S3ClientTelemetryContext get();
}
