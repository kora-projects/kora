package io.koraframework.s3.client.kora.telemetry;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.semconv.ErrorAttributes;
import io.opentelemetry.semconv.incubating.AwsIncubatingAttributes;
import io.opentelemetry.semconv.incubating.RpcIncubatingAttributes;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.concurrent.TimeUnit;

public class DefaultS3ClientObservation implements S3ClientObservation {

    private final long startedRequest = System.nanoTime();

    protected final String bucket;
    protected final String operation;
    protected final S3ClientTelemetryConfig config;
    protected final Span span;
    protected final Logger logger;
    protected final Meter.MeterProvider<Timer> duration;

    @Nullable
    protected String awsKey;
    @Nullable
    protected String uploadId;
    @Nullable
    protected String requestId;
    @Nullable
    protected String extendedRequestId;
    @Nullable
    private Throwable error;

    public DefaultS3ClientObservation(String bucket,
                                      String operation,
                                      S3ClientTelemetryConfig config,
                                      Span span,
                                      Logger logger,
                                      Meter.MeterProvider<Timer> duration) {
        this.operation = operation;
        this.bucket = bucket;
        this.config = config;
        this.span = span;
        this.logger = logger;
        this.duration = duration;
    }

    @Override
    public void observeKey(String key) {
        this.awsKey = key;
        this.span.setAttribute(AwsIncubatingAttributes.AWS_S3_KEY, key);
    }

    @Override
    public void observeUploadId(String uploadId) {
        this.uploadId = uploadId;
        this.span.setAttribute(AwsIncubatingAttributes.AWS_S3_UPLOAD_ID, uploadId);
    }

    @Override
    public void observeAwsRequestId(@Nullable String amxRequestId) {
        this.requestId = amxRequestId;
        this.span.setAttribute(AwsIncubatingAttributes.AWS_REQUEST_ID, amxRequestId);
    }

    @Override
    public void observeAwsExtendedId(@Nullable String amxRequestId) {
        this.extendedRequestId = amxRequestId;
        this.span.setAttribute(AwsIncubatingAttributes.AWS_EXTENDED_REQUEST_ID, amxRequestId);
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
        var errorValue = (error == null) ? "" : this.error.getClass().getCanonicalName();

        if (error == null) {
            this.span.setStatus(StatusCode.OK);
            this.logger.debug("KoraS3Client completed operation '{}' on bucket: {}", operation, bucket);
        } else {
            this.span.setStatus(StatusCode.ERROR, errorValue);
            this.span.recordException(error);
            this.logger.warn("KoraS3Client failed operation '{}' on bucket '{}' due to: {}", operation, bucket, error.getMessage());
        }

        if (config.metrics().enabled()) {
            var took = System.nanoTime() - this.startedRequest;
            var meter = this.duration.withTags(Tags.of(
                Tag.of(RpcIncubatingAttributes.RPC_METHOD.getKey(), operation),
                Tag.of(AwsIncubatingAttributes.AWS_S3_BUCKET.getKey(), bucket),
                Tag.of(ErrorAttributes.ERROR_TYPE.getKey(), errorValue)
            ));

            meter.record(took, TimeUnit.NANOSECONDS);
        }

        this.span.end();
    }
}
