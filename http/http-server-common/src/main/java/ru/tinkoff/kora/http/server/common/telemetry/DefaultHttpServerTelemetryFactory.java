package ru.tinkoff.kora.http.server.common.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.http.server.common.HttpServerConfig;

public final class DefaultHttpServerTelemetryFactory implements HttpServerTelemetryFactory {

    @Nullable
    private final HttpServerLoggerFactory logger;
    @Nullable
    private final HttpServerMetricsFactory metrics;
    @Nullable
    private final HttpServerTracerFactory tracer;

    public DefaultHttpServerTelemetryFactory(@Nullable HttpServerLoggerFactory logger,
                                             @Nullable HttpServerMetricsFactory metrics,
                                             @Nullable HttpServerTracerFactory tracer) {
        this.logger = logger;
        this.metrics = metrics;
        this.tracer = tracer;
    }

    @Deprecated
    @Nullable
    @Override
    public HttpServerTelemetry get(HttpServerTelemetryConfig telemetryConfig) {
        return this.get(telemetryConfig, null);
    }

    @Nullable
    @Override
    public HttpServerTelemetry get(HttpServerTelemetryConfig telemetryConfig, @Nullable HttpServerConfig serverConfig) {
        var metrics = this.metrics == null ? null : this.metrics.get(telemetryConfig.metrics(), serverConfig);
        var logging = this.logger == null ? null : this.logger.get(telemetryConfig.logging(), serverConfig);
        var tracer = this.tracer == null ? null : this.tracer.get(telemetryConfig.tracing(), serverConfig);
        if (metrics == null && logging == null && tracer == null) {
            return null;
        }

        return new DefaultHttpServerTelemetry(metrics, logging, tracer);
    }
}
