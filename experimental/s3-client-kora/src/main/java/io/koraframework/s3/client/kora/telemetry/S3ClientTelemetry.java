package io.koraframework.s3.client.kora.telemetry;

public interface S3ClientTelemetry {

    S3ClientObservation observe(String operation, String bucket);
}
