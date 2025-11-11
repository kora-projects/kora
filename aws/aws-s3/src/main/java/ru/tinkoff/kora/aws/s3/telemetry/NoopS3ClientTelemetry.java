package ru.tinkoff.kora.aws.s3.telemetry;

public class NoopS3ClientTelemetry implements S3ClientTelemetry {
    public static final NoopS3ClientTelemetry INSTANCE = new NoopS3ClientTelemetry();

    @Override
    public S3ClientObservation observe(String headObject, String bucket) {
        return NoopS3ClientObservation.INSTANCE;
    }
}
