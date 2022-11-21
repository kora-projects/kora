package ru.tinkoff.kora.http.client.common.telemetry;

import javax.annotation.Nullable;

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
    public HttpClientTelemetry get(String clientName) {
        return new DefaultHttpClientTelemetry(
            this.tracingFactory == null ? null : this.tracingFactory.get(clientName),
            this.metricsFactory == null ? null : this.metricsFactory.get(clientName),
            this.loggerFactory == null ? null : this.loggerFactory.get(clientName)
        );
    }
}
