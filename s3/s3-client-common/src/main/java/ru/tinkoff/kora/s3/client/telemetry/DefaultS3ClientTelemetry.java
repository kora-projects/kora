package ru.tinkoff.kora.s3.client.telemetry;

import jakarta.annotation.Nullable;

import java.net.URI;
import java.util.concurrent.atomic.AtomicReference;

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
    public S3ClientTelemetryContext get(@Nullable String operation,
                                        @Nullable String bucket) {
        var start = System.nanoTime();
        final S3ClientTracer.S3ClientSpan span;
        if (tracer != null) {
            span = tracer.createSpan(operation, bucket);
        } else {
            span = null;
        }

        return new S3ClientTelemetryContext() {

            record Operation(String method, String path, URI uri, String host, int port, @Nullable Long contentLength) {}

            private final AtomicReference<Operation> opRef = new AtomicReference<>();

            @Override
            public void prepared(String method, String path, URI uri, String host, int port, @Nullable Long contentLength) {
                opRef.set(new Operation(method, path, uri, host, port, contentLength));
                if (logger != null) {
                    logger.logRequest(operation, bucket, method, path, contentLength);
                }
                if (span != null) {
                    span.prepared(method, path, uri, host, port, contentLength);
                }
            }

            @Override
            public void close(int statusCode, @Nullable Throwable exception) {
                var end = System.nanoTime();
                var processingTime = end - start;
                Operation op = opRef.get();
                if (metrics != null) {
                    metrics.record(operation, bucket, op.method, statusCode, processingTime, exception);
                }
                if (logger != null) {
                    logger.logResponse(operation, bucket, op.method, op.path, statusCode, processingTime, exception);
                }
                if (span != null) {
                    span.close(statusCode, exception);
                }
            }
        };
    }
}
