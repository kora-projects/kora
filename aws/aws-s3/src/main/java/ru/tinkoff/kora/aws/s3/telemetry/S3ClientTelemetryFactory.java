package ru.tinkoff.kora.aws.s3.telemetry;

import ru.tinkoff.kora.aws.s3.S3Config;

public interface S3ClientTelemetryFactory {
    S3ClientTelemetry get(S3Config config);
}
