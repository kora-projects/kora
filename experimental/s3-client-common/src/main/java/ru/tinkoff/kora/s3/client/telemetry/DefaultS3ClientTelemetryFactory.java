package ru.tinkoff.kora.s3.client.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.net.URI;

public final class DefaultS3ClientTelemetryFactory implements S3ClientTelemetryFactory {

    private static final S3ClientTelemetry.S3ClientTelemetryContext EMPTY_CTX = new S3ClientTelemetry.S3ClientTelemetryContext() {
        @Override
        public void prepared(String method, String path, URI uri, String host, int port, Long contentLength) {}

        @Override
        public void close(int statusCode, @Nullable Throwable exception) {}
    };
    private static final S3ClientTelemetry EMPTY_TELEMETRY = (operation, bucket) -> EMPTY_CTX;

    private final S3ClientLoggerFactory loggerFactory;
    private final S3ClientTracerFactory tracingFactory;
    private final S3ClientMetricsFactory metricsFactory;

    public DefaultS3ClientTelemetryFactory(@Nullable S3ClientLoggerFactory loggerFactory,
                                           @Nullable S3ClientTracerFactory tracingFactory,
                                           @Nullable S3ClientMetricsFactory metricsFactory) {
        this.loggerFactory = loggerFactory;
        this.tracingFactory = tracingFactory;
        this.metricsFactory = metricsFactory;
    }

    @Override
    public S3ClientTelemetry get(TelemetryConfig config, String clientName) {
        var logger = this.loggerFactory == null ? null : this.loggerFactory.get(config.logging(), clientName);
        var metrics = this.metricsFactory == null ? null : this.metricsFactory.get(config.metrics(), clientName);
        var tracer = this.tracingFactory == null ? null : this.tracingFactory.get(config.tracing(), clientName);
        if (metrics == null && tracer == null && logger == null) {
            return EMPTY_TELEMETRY;
        }

        return new DefaultS3ClientTelemetry(tracer, metrics, logger);
    }
}
