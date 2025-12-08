package ru.tinkoff.kora.aws.s3.telemetry;

import ru.tinkoff.kora.aws.s3.S3ClientConfig;

public interface S3ClientTelemetryFactory {
    S3ClientTelemetry get(S3ClientConfig config);
}
