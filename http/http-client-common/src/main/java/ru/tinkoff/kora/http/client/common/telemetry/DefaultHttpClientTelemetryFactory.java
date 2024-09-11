package ru.tinkoff.kora.http.client.common.telemetry;

import jakarta.annotation.Nullable;

public final class DefaultHttpClientTelemetryFactory implements HttpClientTelemetryFactory {
    @Nullable
    private final HttpClientLoggerFactory loggerFactory;
    @Nullable
    private final HttpClientTracerFactory tracingFactory;
    @Nullable
    private final HttpClientMetricsFactory metricsFactory;

    public DefaultHttpClientTelemetryFactory(@Nullable HttpClientLoggerFactory loggerFactory, @Nullable HttpClientTracerFactory tracingFactory, @Nullable HttpClientMetricsFactory metricsFactory) {
        this.loggerFactory = loggerFactory;
        this.tracingFactory = tracingFactory;
        this.metricsFactory = metricsFactory;
    }

    @Override
    @Nullable
    public HttpClientTelemetry get(HttpClientTelemetryConfig config, String clientName) {
        var tracing = this.tracingFactory == null ? null : this.tracingFactory.get(config.tracing(), clientName);
        var metrics = this.metricsFactory == null ? null : this.metricsFactory.get(config.metrics(), clientName);
        var logger = this.loggerFactory == null ? null : this.loggerFactory.get(config.logging(), clientName);

        if (tracing == null && metrics == null && logger == null) {
            return null;
        }
        return new DefaultHttpClientTelemetry(
            tracing, metrics, logger
        );
    }
}
