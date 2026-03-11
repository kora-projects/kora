package io.koraframework.s3.client.telemetry;

import io.koraframework.s3.client.S3ClientConfig;

public interface S3ClientTelemetryFactory {
    S3ClientTelemetry get(S3ClientConfig config);
}
