package io.koraframework.s3.client.aws.telemetry;

public final class NoopAwsS3ClientTelemetry implements AwsS3ClientTelemetry {

    public static final NoopAwsS3ClientTelemetry INSTANCE = new NoopAwsS3ClientTelemetry();

    private NoopAwsS3ClientTelemetry() {}

    @Override
    public AwsS3ClientObservation observe(String operation, String bucket) {
        return NoopAwsS3ClientObservation.INSTANCE;
    }
}
