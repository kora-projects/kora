package ru.tinkoff.kora.s3.client.telemetry;

import jakarta.annotation.Nullable;

public final class DefaultS3KoraClientTelemetry implements S3KoraClientTelemetry {

    private final S3KoraClientLogger logger;
    private final S3KoraClientMetrics metrics;
    private final S3KoraClientTracer tracer;

    public DefaultS3KoraClientTelemetry(@Nullable S3KoraClientLogger logger,
                                        @Nullable S3KoraClientMetrics metrics,
                                        @Nullable S3KoraClientTracer tracer) {
        this.logger = logger;
        this.metrics = metrics;
        this.tracer = tracer;
    }

    @Override
    public S3KoraClientTelemetryContext get(String operation,
                                            String bucket,
                                            @Nullable String key,
                                            @Nullable Long contentLength) {
        var start = System.nanoTime();
        final S3KoraClientTracer.S3KoraClientSpan span;
        if (tracer != null) {
            span = tracer.createSpan(operation, bucket, key, contentLength);
        } else {
            span = null;
        }

        return exception -> {
            var end = System.nanoTime();
            var processingTime = end - start;
            if (metrics != null) {
                metrics.record(operation, bucket, key, processingTime, exception);
            }
            if (logger != null) {
                logger.logResponse(operation, bucket, key, processingTime, exception);
            }
            if (span != null) {
                span.close(exception);
            }
        };
    }
}
