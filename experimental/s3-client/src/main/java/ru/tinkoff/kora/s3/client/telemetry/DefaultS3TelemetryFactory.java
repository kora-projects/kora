package ru.tinkoff.kora.s3.client.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

public final class DefaultS3TelemetryFactory implements S3TelemetryFactory {

//    private static final S3ClientTelemetry.S3ClientTelemetryContext EMPTY_CTX = new S3ClientTelemetry.S3ClientTelemetryContext() {
//        @Override
//        public void prepared(String method, String bucket, String key, Long contentLength) {}
//
//        @Override
//        public void close(String method, String bucket, String key, int statusCode, @Nullable S3Exception exception) {}
//    };
//    private static final S3ClientTelemetry EMPTY_TELEMETRY = () -> EMPTY_CTX;

    private final S3LoggerFactory loggerFactory;
    private final S3TracerFactory tracingFactory;
    private final S3MetricsFactory metricsFactory;

    public DefaultS3TelemetryFactory(@Nullable S3LoggerFactory loggerFactory,
                                     @Nullable S3TracerFactory tracingFactory,
                                     @Nullable S3MetricsFactory metricsFactory) {
        this.loggerFactory = loggerFactory;
        this.tracingFactory = tracingFactory;
        this.metricsFactory = metricsFactory;
    }

    @Override
    public S3Telemetry get(TelemetryConfig config, Class<?> client) {
//        var logger = this.loggerFactory == null ? null : this.loggerFactory.get(config.logging(), client);
//        var metrics = this.metricsFactory == null ? null : this.metricsFactory.get(config.metrics(), client);
//        var tracer = this.tracingFactory == null ? null : this.tracingFactory.get(config.tracing(), client);
//        if (metrics == null && tracer == null && logger == null) {
//            return EMPTY_TELEMETRY;
//        }
//
//        return new DefaultS3Telemetry(tracer, metrics, logger);
        return null;
    }
}
