package ru.tinkoff.kora.s3.client.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.s3.client.S3Exception;

public interface S3KoraClientTracer {

    interface S3KoraClientSpan {

        void close(@Nullable S3Exception exception);
    }

    S3KoraClientSpan createSpan(String operation,
                                String bucket,
                                @Nullable String key,
                                @Nullable Long contentLength);
}
