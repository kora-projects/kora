package io.koraframework.bpmn.operaton.rest.telemetry.impl;

import io.koraframework.bpmn.operaton.rest.telemetry.OperatonRestTelemetry;
import io.koraframework.bpmn.operaton.rest.telemetry.OperatonRestTelemetryFactory;
import io.koraframework.http.server.common.telemetry.HttpServerTelemetryConfig;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;
import org.jspecify.annotations.Nullable;

public class DefaultOperatonRestTelemetryFactory implements OperatonRestTelemetryFactory {

    public static final Tracer NOOP_TRACER = TracerProvider.noop().get("operaton-rest");
    public static final MeterRegistry NOOP_METER_REGISTRY = new CompositeMeterRegistry();

    @Nullable
    private final Tracer tracer;
    @Nullable
    private final MeterRegistry meterRegistry;
    @Nullable
    private final DefaultOperatonRestLoggerFactory loggerFactory;
    @Nullable
    private final DefaultOperatonRestMetricsFactory metricsFactory;

    public DefaultOperatonRestTelemetryFactory(@Nullable Tracer tracer,
                                               @Nullable MeterRegistry meterRegistry,
                                               @Nullable DefaultOperatonRestLoggerFactory loggerFactory,
                                               @Nullable DefaultOperatonRestMetricsFactory metricsFactory) {
        this.tracer = tracer;
        this.meterRegistry = meterRegistry;
        this.loggerFactory = loggerFactory;
        this.metricsFactory = metricsFactory;
    }

    @Override
    public OperatonRestTelemetry get(HttpServerTelemetryConfig config) {
        var traceEnabled = this.tracer != null && config.tracing().enabled();
        var metricEnabled = this.meterRegistry != null && config.metrics().enabled();
        if (!traceEnabled && !metricEnabled && !config.logging().enabled()) {
            return NoopOperatonRestTelemetry.INSTANCE;
        }

        var tracer = traceEnabled ? this.tracer : NOOP_TRACER;
        var meterRegistry = metricEnabled ? this.meterRegistry : NOOP_METER_REGISTRY;

        final DefaultOperatonRestMetricsFactory enabledMetricsFactory;
        if (metricEnabled) {
            enabledMetricsFactory = this.metricsFactory != null
                ? this.metricsFactory
                : DefaultOperatonRestMetricsFactory.INSTANCE;
        } else {
            enabledMetricsFactory = NoopOperatonRestMetricsFactory.INSTANCE;
        }

        final DefaultOperatonRestLoggerFactory enabledLoggerFactory;
        if (config.logging().enabled()) {
            enabledLoggerFactory = this.loggerFactory != null
                ? this.loggerFactory
                : DefaultOperatonRestLoggerFactory.INSTANCE;
        } else {
            enabledLoggerFactory = NoopOperatonRestLoggerFactory.INSTANCE;
        }

        return new DefaultOperatonRestTelemetry(config, traceEnabled, metricEnabled, tracer, meterRegistry, enabledLoggerFactory, enabledMetricsFactory);
    }
}
