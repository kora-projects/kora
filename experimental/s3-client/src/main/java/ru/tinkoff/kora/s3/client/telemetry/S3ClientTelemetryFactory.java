package ru.tinkoff.kora.s3.client.telemetry;

import ru.tinkoff.kora.s3.client.S3ClientConfig;

public interface S3ClientTelemetryFactory {
    S3ClientTelemetry get(S3ClientConfig config);
}
