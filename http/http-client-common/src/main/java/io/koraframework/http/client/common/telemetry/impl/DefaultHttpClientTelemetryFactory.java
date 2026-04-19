package io.koraframework.http.client.common.telemetry.impl;

import io.koraframework.http.client.common.telemetry.HttpClientTelemetry;
import io.koraframework.http.client.common.telemetry.HttpClientTelemetryConfig;
import io.koraframework.http.client.common.telemetry.HttpClientTelemetryFactory;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;
import org.jspecify.annotations.Nullable;

public final class DefaultHttpClientTelemetryFactory implements HttpClientTelemetryFactory {

    private static final Tracer NOOP_TRACER = TracerProvider.noop().get("http-client");
    private static final MeterRegistry NOOP_METER_REGISTRY = new CompositeMeterRegistry();

    @Nullable
    private final Tracer tracer;
    @Nullable
    private final MeterRegistry meterRegistry;

    public DefaultHttpClientTelemetryFactory(@Nullable Tracer tracer,
                                             @Nullable MeterRegistry meterRegistry) {
        this.tracer = tracer;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public HttpClientTelemetry get(String clientName, String clientImpl, HttpClientTelemetryConfig config) {
        var traceEnabled = this.tracer != null && config.tracing().enabled();
        var metricEnabled = this.meterRegistry != null && config.metrics().enabled();
        if (!traceEnabled && !metricEnabled && !config.logging().enabled()) {
            return NoopHttpClientTelemetry.INSTANCE;
        }

        var meterRegistry = metricEnabled ? this.meterRegistry : NOOP_METER_REGISTRY;
        var metrics = (metricEnabled)
            ? new DefaultHttpClientMetrics(clientName, clientImpl, meterRegistry, config.metrics())
            : NoopHttpClientMetrics.INSTANCE;

        var logger = (config.logging().enabled())
            ? new DefaultHttpClientLogger(clientName, clientImpl, config.logging())
            : NoopHttpClientLogger.INSTANCE;

        var tracer = traceEnabled ? this.tracer : NOOP_TRACER;
        return new DefaultHttpClientTelemetry(clientName, clientImpl, config, tracer, logger, metrics);
    }
}
