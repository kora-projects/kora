package ru.tinkoff.kora.s3.client.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.s3.client.S3Exception;

public interface S3KoraClientMetrics {

    void record(String operation,
                String bucket,
                @Nullable String key,
                long processingTimeNanos,
                @Nullable S3Exception exception);
}
