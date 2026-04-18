package io.koraframework.s3.client.aws.telemetry;

public interface AwsS3ClientTelemetryFactory {

    AwsS3ClientTelemetry get(AwsS3ClientTelemetryConfig config);
}
