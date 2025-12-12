package ru.tinkoff.kora.s3.client.telemetry;

public interface S3ClientTelemetry {
    S3ClientObservation observe(String operation, String bucket);

}
