package ru.tinkoff.kora.s3.client.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.s3.client.S3Exception;

public interface S3KoraClientTelemetry {

    interface S3KoraClientTelemetryContext {

        default void close() {
            close(null);
        }

        void close(@Nullable S3Exception exception);
    }

    S3KoraClientTelemetryContext get(String operation,
                                     String bucket,
                                     @Nullable String key,
                                     @Nullable Long contentLength);
}
