package io.koraframework.s3.client.kora.telemetry.impl;

import io.koraframework.s3.client.kora.telemetry.S3ClientObservation;
import io.koraframework.s3.client.kora.telemetry.S3ClientTelemetry;

public final class NoopS3ClientTelemetry implements S3ClientTelemetry {

    public static final NoopS3ClientTelemetry INSTANCE = new NoopS3ClientTelemetry();

    private NoopS3ClientTelemetry() {}

    @Override
    public S3ClientObservation observe(String operation, String bucket) {
        return NoopS3ClientObservation.INSTANCE;
    }
}
