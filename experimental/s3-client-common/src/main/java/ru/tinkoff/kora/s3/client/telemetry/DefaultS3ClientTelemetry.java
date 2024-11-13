package ru.tinkoff.kora.s3.client.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.s3.client.S3Exception;

public final class DefaultS3ClientTelemetry implements S3ClientTelemetry {

    private final S3ClientLogger logger;
    private final S3ClientMetrics metrics;
    private final S3ClientTracer tracer;

    public DefaultS3ClientTelemetry(@Nullable S3ClientTracer tracer,
                                    @Nullable S3ClientMetrics metrics,
                                    @Nullable S3ClientLogger logger) {
        this.logger = logger;
        this.tracer = tracer;
        this.metrics = metrics;
    }

    @Override
    public S3ClientTelemetryContext get() {
        var start = System.nanoTime();
        final S3ClientTracer.S3ClientSpan span;
        if (tracer != null) {
            span = tracer.createSpan();
        } else {
            span = null;
        }

        return new S3ClientTelemetryContext() {

            @Override
            public void prepared(String method, String bucket, @Nullable String key, @Nullable Long contentLength) {
                if (logger != null) {
                    logger.logRequest(method, bucket, key, contentLength);
                }
                if (span != null) {
                    span.prepared(method, bucket, key, contentLength);
                }
            }

            @Override
            public void close(String method, String bucket, @Nullable String key, int statusCode, @Nullable S3Exception exception) {
                var end = System.nanoTime();
                var processingTime = end - start;
                if (metrics != null) {
                    metrics.record(method, bucket, key, statusCode, processingTime, exception);
                }
                if (logger != null) {
                    logger.logResponse(method, bucket, key, statusCode, processingTime, exception);
                }
                if (span != null) {
                    span.close(statusCode, exception);
                }
            }
        };
    }
}
