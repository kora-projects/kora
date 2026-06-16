package io.koraframework.camunda.engine.bpmn.telemetry.impl;

import io.koraframework.camunda.engine.bpmn.CamundaEngineBpmnConfig;
import io.koraframework.camunda.engine.bpmn.telemetry.CamundaEngineBpmnTelemetry;
import io.koraframework.camunda.engine.bpmn.telemetry.CamundaEngineBpmnTelemetryFactory;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;
import org.jspecify.annotations.Nullable;

public class DefaultCamundaEngineBpmnTelemetryFactory implements CamundaEngineBpmnTelemetryFactory {

    public static final Tracer NOOP_TRACER = TracerProvider.noop().get("camunda-engine-bpmn");
    public static final MeterRegistry NOOP_METER_REGISTRY = new CompositeMeterRegistry();

    @Nullable
    private final Tracer tracer;
    @Nullable
    private final MeterRegistry meterRegistry;
    @Nullable
    private final DefaultCamundaEngineBpmnLoggerFactory loggerFactory;
    @Nullable
    private final DefaultCamundaEngineBpmnMetricsFactory metricsFactory;

    public DefaultCamundaEngineBpmnTelemetryFactory(@Nullable Tracer tracer,
                                                    @Nullable MeterRegistry meterRegistry,
                                                    @Nullable DefaultCamundaEngineBpmnLoggerFactory loggerFactory,
                                                    @Nullable DefaultCamundaEngineBpmnMetricsFactory metricsFactory) {
        this.tracer = tracer;
        this.meterRegistry = meterRegistry;
        this.loggerFactory = loggerFactory;
        this.metricsFactory = metricsFactory;
    }

    @Override
    public CamundaEngineBpmnTelemetry get(CamundaEngineBpmnConfig.CamundaTelemetryConfig config) {
        var traceEnabled = this.tracer != null && config.tracing().enabled();
        var metricEnabled = this.meterRegistry != null && config.metrics().enabled();
        if (!traceEnabled && !metricEnabled && !config.logging().enabled()) {
            return NoopCamundaEngineBpmnTelemetry.INSTANCE;
        }

        var tracer = traceEnabled ? this.tracer : NOOP_TRACER;
        var meterRegistry = metricEnabled ? this.meterRegistry : NOOP_METER_REGISTRY;

        final DefaultCamundaEngineBpmnMetricsFactory enabledMetricsFactory;
        if (metricEnabled) {
            enabledMetricsFactory = this.metricsFactory != null
                ? this.metricsFactory
                : DefaultCamundaEngineBpmnMetricsFactory.INSTANCE;
        } else {
            enabledMetricsFactory = NoopCamundaEngineBpmnMetricsFactory.INSTANCE;
        }

        final DefaultCamundaEngineBpmnLoggerFactory enabledLoggerFactory;
        if (config.logging().enabled()) {
            enabledLoggerFactory = this.loggerFactory != null
                ? this.loggerFactory
                : DefaultCamundaEngineBpmnLoggerFactory.INSTANCE;
        } else {
            enabledLoggerFactory = NoopCamundaEngineBpmnLoggerFactory.INSTANCE;
        }

        return new DefaultCamundaEngineBpmnTelemetry(config, traceEnabled, metricEnabled, tracer, meterRegistry, enabledLoggerFactory, enabledMetricsFactory);
    }
}
