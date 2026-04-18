package io.koraframework.s3.client.aws.telemetry;

public interface AwsS3ClientTelemetry {

    AwsS3ClientObservation observe(String operation, String bucket);
}
