package io.koraframework.s3.client.aws.telemetry.impl;

import io.koraframework.s3.client.aws.telemetry.AwsS3ClientObservation;
import io.opentelemetry.api.trace.Span;
import org.jspecify.annotations.Nullable;

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
    public void observeAwsRequestId(@Nullable String amxRequestId) {

    }

    @Override
    public void observeAwsExtendedId(@Nullable String amxRequestId) {

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
