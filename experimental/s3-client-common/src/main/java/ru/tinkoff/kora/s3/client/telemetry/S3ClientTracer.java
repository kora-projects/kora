package ru.tinkoff.kora.s3.client.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.s3.client.S3Exception;

public interface S3ClientTracer {

    interface S3ClientSpan {

        void prepared(String method,
                      String bucket,
                      @Nullable String key,
                      @Nullable Long contentLength);

        void close(int statusCode, @Nullable S3Exception exception);
    }

    S3ClientSpan createSpan();
}
