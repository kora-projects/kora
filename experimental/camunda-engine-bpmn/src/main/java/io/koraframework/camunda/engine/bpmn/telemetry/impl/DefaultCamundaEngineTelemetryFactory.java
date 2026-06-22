package io.koraframework.camunda.engine.bpmn.telemetry.impl;

import io.koraframework.camunda.engine.bpmn.telemetry.CamundaEngineTelemetry;
import io.koraframework.camunda.engine.bpmn.telemetry.CamundaEngineTelemetryConfig;
import io.koraframework.camunda.engine.bpmn.telemetry.CamundaEngineTelemetryFactory;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;
import org.jspecify.annotations.Nullable;

public class DefaultCamundaEngineTelemetryFactory implements CamundaEngineTelemetryFactory {

    public static final Tracer NOOP_TRACER = TracerProvider.noop().get("camunda-engine-bpmn");
    public static final MeterRegistry NOOP_METER_REGISTRY = new CompositeMeterRegistry();

    @Nullable
    private final Tracer tracer;
    @Nullable
    private final MeterRegistry meterRegistry;
    @Nullable
    private final DefaultCamundaEngineLoggerFactory loggerFactory;
    @Nullable
    private final DefaultCamundaEngineMetricsFactory metricsFactory;

    public DefaultCamundaEngineTelemetryFactory(@Nullable Tracer tracer,
                                                @Nullable MeterRegistry meterRegistry,
                                                @Nullable DefaultCamundaEngineLoggerFactory loggerFactory,
                                                @Nullable DefaultCamundaEngineMetricsFactory metricsFactory) {
        this.tracer = tracer;
        this.meterRegistry = meterRegistry;
        this.loggerFactory = loggerFactory;
        this.metricsFactory = metricsFactory;
    }

    @Override
    public CamundaEngineTelemetry get(CamundaEngineTelemetryConfig config) {
        var traceEnabled = this.tracer != null && config.tracing().enabled();
        var metricEnabled = this.meterRegistry != null && config.metrics().enabled();
        if (!traceEnabled && !metricEnabled && !config.logging().enabled()) {
            return NoopCamundaEngineTelemetry.INSTANCE;
        }

        var tracer = traceEnabled ? this.tracer : NOOP_TRACER;
        var meterRegistry = metricEnabled ? this.meterRegistry : NOOP_METER_REGISTRY;

        final DefaultCamundaEngineMetricsFactory enabledMetricsFactory;
        if (metricEnabled) {
            enabledMetricsFactory = this.metricsFactory != null
                ? this.metricsFactory
                : DefaultCamundaEngineMetricsFactory.INSTANCE;
        } else {
            enabledMetricsFactory = NoopCamundaEngineMetricsFactory.INSTANCE;
        }

        final DefaultCamundaEngineLoggerFactory enabledLoggerFactory;
        if (config.logging().enabled()) {
            enabledLoggerFactory = this.loggerFactory != null
                ? this.loggerFactory
                : DefaultCamundaEngineLoggerFactory.INSTANCE;
        } else {
            enabledLoggerFactory = NoopCamundaEngineLoggerFactory.INSTANCE;
        }

        return build(config, tracer, meterRegistry, enabledMetricsFactory, enabledLoggerFactory);
    }

    protected CamundaEngineTelemetry build(CamundaEngineTelemetryConfig config,
                                           Tracer tracer,
                                           MeterRegistry meterRegistry,
                                           DefaultCamundaEngineMetricsFactory metricsFactory,
                                           DefaultCamundaEngineLoggerFactory loggerFactory) {
        return new DefaultCamundaEngineTelemetry(config, tracer, meterRegistry, metricsFactory, loggerFactory);
    }
}
