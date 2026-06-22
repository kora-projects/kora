package io.koraframework.http.server.common.telemetry.impl;

import io.koraframework.http.server.common.telemetry.HttpServerTelemetry;
import io.koraframework.http.server.common.telemetry.HttpServerTelemetryConfig;
import io.koraframework.http.server.common.telemetry.HttpServerTelemetryFactory;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;
import org.jspecify.annotations.Nullable;

public class DefaultHttpServerTelemetryFactory implements HttpServerTelemetryFactory {

    public static final Tracer NOOP_TRACER = TracerProvider.noop().get("http-server");
    public static final MeterRegistry NOOP_METER_REGISTRY = new CompositeMeterRegistry();

    @Nullable
    private final MeterRegistry meterRegistry;
    @Nullable
    private final Tracer tracer;
    @Nullable
    private final DefaultHttpServerLoggerFactory loggerFactory;
    @Nullable
    private final DefaultHttpServerMetricsFactory metricsFactory;
    @Nullable
    private final DefaultHttpServerBodyConverter bodyLogger;

    public DefaultHttpServerTelemetryFactory(@Nullable MeterRegistry meterRegistry,
                                             @Nullable Tracer tracer,
                                             @Nullable DefaultHttpServerLoggerFactory loggerFactory,
                                             @Nullable DefaultHttpServerMetricsFactory metricsFactory,
                                             @Nullable DefaultHttpServerBodyConverter bodyLogger) {
        this.meterRegistry = meterRegistry;
        this.tracer = tracer;
        this.loggerFactory = loggerFactory;
        this.metricsFactory = metricsFactory;
        this.bodyLogger = bodyLogger;
    }

    @Override
    public HttpServerTelemetry get(HttpServerTelemetryConfig config) {
        var traceEnabled = this.tracer != null && config.tracing().enabled();
        var metricEnabled = this.meterRegistry != null && config.metrics().enabled();
        if (!traceEnabled && !metricEnabled && !config.logging().enabled()) {
            return NoopHttpServerTelemetry.INSTANCE;
        }

        var tracer = traceEnabled ? this.tracer : NOOP_TRACER;
        var meterRegistry = metricEnabled ? this.meterRegistry : NOOP_METER_REGISTRY;

        final DefaultHttpServerMetricsFactory enabledMetricsFactory;
        if (metricEnabled) {
            enabledMetricsFactory = this.metricsFactory != null
                ? this.metricsFactory
                : DefaultHttpServerMetricsFactory.INSTANCE;
        } else {
            enabledMetricsFactory = NoopHttpServerMetricsFactory.INSTANCE;
        }

        final DefaultHttpServerLoggerFactory enabledLoggerFactory;
        if (config.logging().enabled()) {
            enabledLoggerFactory = this.loggerFactory != null
                ? this.loggerFactory
                : DefaultHttpServerLoggerFactory.INSTANCE;
        } else {
            enabledLoggerFactory = NoopHttpServerLoggerFactory.INSTANCE;
        }

        return build(config, tracer, meterRegistry, enabledMetricsFactory, enabledLoggerFactory, bodyLogger != null ? bodyLogger : new DefaultHttpServerBodyConverter());
    }

    protected HttpServerTelemetry build(HttpServerTelemetryConfig config,
                                        Tracer tracer,
                                        MeterRegistry meterRegistry,
                                        DefaultHttpServerMetricsFactory metricsFactory,
                                        DefaultHttpServerLoggerFactory loggerFactory,
                                        DefaultHttpServerBodyConverter loggerBodyConverter) {
        return new DefaultHttpServerTelemetry(config, tracer, meterRegistry, metricsFactory, loggerFactory, loggerBodyConverter);
    }
}
