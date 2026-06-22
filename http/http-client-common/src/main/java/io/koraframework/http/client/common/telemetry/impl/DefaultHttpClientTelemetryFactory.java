package io.koraframework.http.client.common.telemetry.impl;

import io.koraframework.http.client.common.telemetry.HttpClientTelemetry;
import io.koraframework.http.client.common.telemetry.HttpClientTelemetryConfig;
import io.koraframework.http.client.common.telemetry.HttpClientTelemetryFactory;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;
import org.jspecify.annotations.Nullable;

public class DefaultHttpClientTelemetryFactory implements HttpClientTelemetryFactory {

    public static final Tracer NOOP_TRACER = TracerProvider.noop().get("http-client");
    public static final MeterRegistry NOOP_METER_REGISTRY = new CompositeMeterRegistry();

    @Nullable
    private final Tracer tracer;
    @Nullable
    private final MeterRegistry meterRegistry;
    @Nullable
    private final DefaultHttpClientLoggerFactory loggerFactory;
    @Nullable
    private final DefaultHttpClientMetricsFactory metricsFactory;
    @Nullable
    private final DefaultHttpClientBodyConverter loggerBodyConverter;

    public DefaultHttpClientTelemetryFactory(@Nullable Tracer tracer,
                                             @Nullable MeterRegistry meterRegistry,
                                             @Nullable DefaultHttpClientLoggerFactory loggerFactory,
                                             @Nullable DefaultHttpClientMetricsFactory metricsFactory,
                                             @Nullable DefaultHttpClientBodyConverter loggerBodyConverter) {
        this.tracer = tracer;
        this.meterRegistry = meterRegistry;
        this.loggerFactory = loggerFactory;
        this.metricsFactory = metricsFactory;
        this.loggerBodyConverter = loggerBodyConverter;
    }

    @Override
    public HttpClientTelemetry get(String clientConfigPath, String clientCanonicalName, HttpClientTelemetryConfig config) {
        var traceEnabled = this.tracer != null && config.tracing().enabled();
        var metricEnabled = this.meterRegistry != null && config.metrics().enabled();
        if (!traceEnabled && !metricEnabled && !config.logging().enabled()) {
            return NoopHttpClientTelemetry.INSTANCE;
        }

        var tracer = traceEnabled ? this.tracer : NOOP_TRACER;
        var meterRegistry = metricEnabled ? this.meterRegistry : NOOP_METER_REGISTRY;

        final DefaultHttpClientMetricsFactory enabledMetricsFactory;
        if (metricEnabled) {
            enabledMetricsFactory = this.metricsFactory != null
                ? this.metricsFactory
                : DefaultHttpClientMetricsFactory.INSTANCE;
        } else {
            enabledMetricsFactory = NoopHttpClientMetricsFactory.INSTANCE;
        }

        final DefaultHttpClientLoggerFactory enabledLoggerFactory;
        if (config.logging().enabled()) {
            enabledLoggerFactory = this.loggerFactory != null
                ? this.loggerFactory
                : DefaultHttpClientLoggerFactory.INSTANCE;
        } else {
            enabledLoggerFactory = NoopHttpClientLoggerFactory.INSTANCE;
        }

        return build(clientConfigPath, clientCanonicalName, config, tracer, meterRegistry, enabledMetricsFactory, enabledLoggerFactory, loggerBodyConverter != null ? loggerBodyConverter : new DefaultHttpClientBodyConverter());
    }

    protected HttpClientTelemetry build(String clientConfigPath,
                                        String clientCanonicalName,
                                        HttpClientTelemetryConfig config,
                                        Tracer tracer,
                                        MeterRegistry meterRegistry,
                                        DefaultHttpClientMetricsFactory metricsFactory,
                                        DefaultHttpClientLoggerFactory loggerFactory,
                                        DefaultHttpClientBodyConverter loggerBodyConverter) {
        return new DefaultHttpClientTelemetry(clientConfigPath, clientCanonicalName, config, tracer, meterRegistry, metricsFactory, loggerFactory, loggerBodyConverter);
    }
}
