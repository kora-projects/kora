package ru.tinkoff.kora.aws.s3.telemetry;

import io.opentelemetry.api.trace.Span;

public class NoopS3ClientObservation implements S3ClientObservation {
    public static final S3ClientObservation INSTANCE = new NoopS3ClientObservation();

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
