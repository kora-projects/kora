package io.koraframework.s3.client.kora.telemetry;

public interface S3ClientTelemetryFactory {

    S3ClientTelemetry get(String clientConfigPath, Class<?> clientType, S3ClientTelemetryConfig config);
}
