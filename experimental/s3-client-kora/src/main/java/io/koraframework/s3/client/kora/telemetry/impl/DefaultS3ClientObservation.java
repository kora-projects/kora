package io.koraframework.s3.client.kora.telemetry.impl;

import io.koraframework.s3.client.kora.telemetry.S3ClientObservation;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.semconv.ErrorAttributes;
import io.opentelemetry.semconv.incubating.AwsIncubatingAttributes;
import org.jspecify.annotations.Nullable;

public class DefaultS3ClientObservation implements S3ClientObservation {

    private final long startedRequest = System.nanoTime();

    protected final String bucket;
    protected final String operation;
    protected final DefaultS3ClientTelemetry.TelemetryContext context;
    protected final DefaultS3ClientLoggerFactory.DefaultS3ClientLogger logger;
    protected final DefaultS3ClientMetricsFactory.DefaultS3ClientMetrics metrics;
    protected final Span span;

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
                                      DefaultS3ClientTelemetry.TelemetryContext context,
                                      DefaultS3ClientLoggerFactory.DefaultS3ClientLogger logger,
                                      DefaultS3ClientMetricsFactory.DefaultS3ClientMetrics metrics,
                                      Span span) {
        this.operation = operation;
        this.bucket = bucket;
        this.context = context;
        this.logger = logger;
        this.metrics = metrics;
        this.span = span;
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
        var processingTimeNanos = System.nanoTime() - this.startedRequest;
        if (error == null) {
            this.span.setStatus(StatusCode.OK);
        } else {
            var errorValue = this.error.getClass().getCanonicalName();
            this.span.setStatus(StatusCode.ERROR, errorValue);
            this.span.setAttribute(ErrorAttributes.ERROR_TYPE.getKey(), errorValue);
            this.span.recordException(error);
        }

        this.metrics.record(operation, bucket, error, this.startedRequest);
        this.logger.logEnd(operation, bucket, error, processingTimeNanos);
        this.span.end();
    }
}
