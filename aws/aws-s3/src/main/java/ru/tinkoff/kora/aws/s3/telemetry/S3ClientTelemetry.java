package ru.tinkoff.kora.aws.s3.telemetry;

public interface S3ClientTelemetry {
    S3ClientObservation observe(String headObject, String bucket);

}
