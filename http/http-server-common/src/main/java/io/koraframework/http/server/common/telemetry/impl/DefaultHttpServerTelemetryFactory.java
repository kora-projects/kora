package io.koraframework.http.server.common.telemetry.impl;

import io.koraframework.http.server.common.telemetry.HttpServerTelemetry;
import io.koraframework.http.server.common.telemetry.HttpServerTelemetryConfig;
import io.koraframework.http.server.common.telemetry.HttpServerTelemetryFactory;
import io.koraframework.micrometer.common.NoopMeterRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;
import org.jspecify.annotations.Nullable;

public class DefaultHttpServerTelemetryFactory implements HttpServerTelemetryFactory {

    private static final class TracerHolder {
        private static final Tracer NOOP_TRACER = TracerProvider.noop().get("http-server");
    }

    public static final MeterRegistry NOOP_METER_REGISTRY = NoopMeterRegistry.INSTANCE;

    static Tracer noopTracer() {
        return TracerHolder.NOOP_TRACER;
    }

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
    public HttpServerTelemetry get(String name, int port, HttpServerTelemetryConfig config) {
        var traceEnabled = tracer != null && config.tracing().enabled();
        var metricEnabled = meterRegistry != null && config.metrics().enabled();
        if (!traceEnabled && !metricEnabled && !config.logging().enabled()) {
            return NoopHttpServerTelemetry.INSTANCE;
        }

        var currentTracer = traceEnabled ? tracer : TracerHolder.NOOP_TRACER;
        var currentMeterRegistry = metricEnabled ? meterRegistry : NOOP_METER_REGISTRY;

        final DefaultHttpServerMetricsFactory enabledMetricsFactory;
        if (metricEnabled) {
            enabledMetricsFactory = metricsFactory != null
                ? metricsFactory
                : DefaultHttpServerMetricsFactory.INSTANCE;
        } else {
            enabledMetricsFactory = NoopHttpServerMetricsFactory.INSTANCE;
        }

        final DefaultHttpServerLoggerFactory enabledLoggerFactory;
        if (config.logging().enabled()) {
            enabledLoggerFactory = loggerFactory != null
                ? loggerFactory
                : DefaultHttpServerLoggerFactory.INSTANCE;
        } else {
            enabledLoggerFactory = NoopHttpServerLoggerFactory.INSTANCE;
        }

        return build(name, port, config, currentTracer, currentMeterRegistry, enabledMetricsFactory, enabledLoggerFactory, bodyLogger != null ? bodyLogger : new DefaultHttpServerBodyConverter());
    }

    protected HttpServerTelemetry build(String name,
                                        int port,
                                        HttpServerTelemetryConfig config,
                                        Tracer tracer,
                                        MeterRegistry meterRegistry,
                                        DefaultHttpServerMetricsFactory metricsFactory,
                                        DefaultHttpServerLoggerFactory loggerFactory,
                                        DefaultHttpServerBodyConverter loggerBodyConverter) {
        return new DefaultHttpServerTelemetry(name, port, config, tracer, meterRegistry, metricsFactory, loggerFactory, loggerBodyConverter);
    }
}
