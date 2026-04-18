package io.koraframework.s3.client.aws.telemetry;

import io.opentelemetry.api.trace.Span;

public final class NoopAwsS3ClientObservation implements AwsS3ClientObservation {

    public static final AwsS3ClientObservation INSTANCE = new NoopAwsS3ClientObservation();

    private NoopAwsS3ClientObservation() {}

    @Override
    public void observeKey(String key) {

    }

    @Override
    public void observeUploadId(String uploadId) {

    }

    @Override
    public void observeAwsRequestId(String amxRequestId) {

    }

    @Override
    public void observeAwsExtendedId(String amxRequestId) {

    }

    @Override
    public Span span() {
        return Span.getInvalid();
    }

    @Override
    public void end() {

    }

    @Override
    public void observeError(Throwable e) {

    }

}
