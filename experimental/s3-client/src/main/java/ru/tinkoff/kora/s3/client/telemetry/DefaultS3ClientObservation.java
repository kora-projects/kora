package ru.tinkoff.kora.s3.client.telemetry;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.semconv.ErrorAttributes;
import io.opentelemetry.semconv.incubating.AwsIncubatingAttributes;
import jakarta.annotation.Nullable;

import java.util.concurrent.TimeUnit;

public class DefaultS3ClientObservation implements S3ClientObservation {
    private final long start = System.nanoTime();
    private final Span span;
    private final Meter.MeterProvider<Timer> duration;
    private Throwable error;

    public DefaultS3ClientObservation(Span span, Meter.MeterProvider<Timer> duration) {
        this.span = span;
        this.duration = duration;
    }

    @Override
    public void observeKey(String key) {
        span.setAttribute(AwsIncubatingAttributes.AWS_S3_KEY, key);
    }

    @Override
    public void observeUploadId(String uploadId) {
        span.setAttribute(AwsIncubatingAttributes.AWS_S3_UPLOAD_ID, uploadId);
    }

    @Override
    public void observeAwsRequestId(@Nullable String amxRequestId) {
        span.setAttribute(AwsIncubatingAttributes.AWS_REQUEST_ID, amxRequestId);
    }

    @Override
    public void observeAwsExtendedId(@Nullable String amxRequestId) {
        span.setAttribute(AwsIncubatingAttributes.AWS_EXTENDED_REQUEST_ID, amxRequestId);
    }

    @Override
    public Span span() {
        return this.span;
    }

    @Override
    public void observeError(Throwable e) {
        this.span.recordException(e);
        this.span.setStatus(StatusCode.ERROR);
        this.error = e;
    }

    @Override
    public void end() {
        var took = System.nanoTime() - this.start;
        if (this.error == null) {
            this.span.setStatus(StatusCode.OK);
            this.duration.withTag(ErrorAttributes.ERROR_TYPE.getKey(), "")
                .record(took, TimeUnit.NANOSECONDS);
        } else {
            this.duration.withTag(ErrorAttributes.ERROR_TYPE.getKey(), this.error.getClass().getCanonicalName())
                .record(took, TimeUnit.NANOSECONDS);
        }
        this.span.end();
    }
}
