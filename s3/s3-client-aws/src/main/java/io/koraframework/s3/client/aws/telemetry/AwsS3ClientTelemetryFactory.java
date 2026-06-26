package io.koraframework.s3.client.aws.telemetry;

public interface AwsS3ClientTelemetryFactory {

    AwsS3ClientTelemetry get(String clientConfigPath, Class<?> clientType, AwsS3ClientTelemetryConfig config);
}
