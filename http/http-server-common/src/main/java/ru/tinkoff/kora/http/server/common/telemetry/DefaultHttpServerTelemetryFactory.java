package ru.tinkoff.kora.http.server.common.telemetry;

import jakarta.annotation.Nullable;

public final class DefaultHttpServerTelemetryFactory implements HttpServerTelemetryFactory {
    @Nullable
    private final HttpServerLoggerFactory logger;
    @Nullable
    private final HttpServerMetricsFactory metrics;
    @Nullable
    private final HttpServerTracerFactory tracer;

    public DefaultHttpServerTelemetryFactory(@Nullable HttpServerLoggerFactory logger, @Nullable HttpServerMetricsFactory metrics, @Nullable HttpServerTracerFactory tracer) {
        this.logger = logger;
        this.metrics = metrics;
        this.tracer = tracer;
    }

    @Override
    @Nullable
    public HttpServerTelemetry get(HttpServerTelemetryConfig config) {
        var metrics = this.metrics == null ? null : this.metrics.get(config.metrics());
        var logging = this.logger == null ? null : this.logger.get(config.logging());
        var tracer = this.tracer == null ? null : this.tracer.get(config.tracing());
        if (metrics == null && logging == null && tracer == null) {
            return null;
        }

        return new DefaultHttpServerTelemetry(
            metrics,
            logging,
            tracer
        );
    }
}
