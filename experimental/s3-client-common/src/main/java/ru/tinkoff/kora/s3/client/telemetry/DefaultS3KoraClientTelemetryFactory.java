package ru.tinkoff.kora.s3.client.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

public final class DefaultS3KoraClientTelemetryFactory implements S3KoraClientTelemetryFactory {

    private static final S3KoraClientTelemetry.S3KoraClientTelemetryContext EMPTY_CTX = exception -> {};
    private static final S3KoraClientTelemetry EMPTY_TELEMETRY = (operation, bucket, key, contentLength) -> EMPTY_CTX;

    private final S3KoraClientLoggerFactory loggerFactory;
    private final S3KoraClientTracerFactory tracingFactory;
    private final S3KoraClientMetricsFactory metricsFactory;

    public DefaultS3KoraClientTelemetryFactory(@Nullable S3KoraClientLoggerFactory loggerFactory,
                                               @Nullable S3KoraClientTracerFactory tracingFactory,
                                               @Nullable S3KoraClientMetricsFactory metricsFactory) {
        this.loggerFactory = loggerFactory;
        this.tracingFactory = tracingFactory;
        this.metricsFactory = metricsFactory;
    }

    @Override
    public S3KoraClientTelemetry get(TelemetryConfig config, Class<?> clientImpl) {
        var logger = this.loggerFactory == null ? null : this.loggerFactory.get(config.logging(), clientImpl);
        var metrics = this.metricsFactory == null ? null : this.metricsFactory.get(config.metrics(), clientImpl);
        var tracer = this.tracingFactory == null ? null : this.tracingFactory.get(config.tracing(), clientImpl);
        if (metrics == null && tracer == null && logger == null) {
            return EMPTY_TELEMETRY;
        }

        return new DefaultS3KoraClientTelemetry(logger, metrics, tracer);
    }
}
