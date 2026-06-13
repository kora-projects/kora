package io.koraframework.bpmn.operaton.engine.telemetry.impl;

import io.koraframework.bpmn.operaton.engine.OperatonEngineBpmnConfig;
import io.koraframework.bpmn.operaton.engine.telemetry.OperatonEngineBpmnTelemetry;
import io.koraframework.bpmn.operaton.engine.telemetry.OperatonEngineBpmnTelemetryFactory;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;
import org.jspecify.annotations.Nullable;

public class DefaultOperatonEngineBpmnTelemetryFactory implements OperatonEngineBpmnTelemetryFactory {

    public static final Tracer NOOP_TRACER = TracerProvider.noop().get("operaton-engine-bpmn");
    public static final MeterRegistry NOOP_METER_REGISTRY = new CompositeMeterRegistry();

    @Nullable
    private final Tracer tracer;
    @Nullable
    private final MeterRegistry meterRegistry;
    @Nullable
    private final DefaultOperatonEngineBpmnLoggerFactory loggerFactory;
    @Nullable
    private final DefaultOperatonEngineBpmnMetricsFactory metricsFactory;

    public DefaultOperatonEngineBpmnTelemetryFactory(@Nullable Tracer tracer,
                                                    @Nullable MeterRegistry meterRegistry,
                                                    @Nullable DefaultOperatonEngineBpmnLoggerFactory loggerFactory,
                                                    @Nullable DefaultOperatonEngineBpmnMetricsFactory metricsFactory) {
        this.tracer = tracer;
        this.meterRegistry = meterRegistry;
        this.loggerFactory = loggerFactory;
        this.metricsFactory = metricsFactory;
    }

    @Override
    public OperatonEngineBpmnTelemetry get(OperatonEngineBpmnConfig.OperatonTelemetryConfig config) {
        var traceEnabled = this.tracer != null && config.tracing().enabled();
        var metricEnabled = this.meterRegistry != null && config.metrics().enabled();
        if (!traceEnabled && !metricEnabled && !config.logging().enabled()) {
            return NoopOperatonEngineBpmnTelemetry.INSTANCE;
        }

        var tracer = traceEnabled ? this.tracer : NOOP_TRACER;
        var meterRegistry = metricEnabled ? this.meterRegistry : NOOP_METER_REGISTRY;

        final DefaultOperatonEngineBpmnMetricsFactory enabledMetricsFactory;
        if (metricEnabled) {
            enabledMetricsFactory = this.metricsFactory != null
                ? this.metricsFactory
                : DefaultOperatonEngineBpmnMetricsFactory.INSTANCE;
        } else {
            enabledMetricsFactory = NoopOperatonEngineBpmnMetricsFactory.INSTANCE;
        }

        final DefaultOperatonEngineBpmnLoggerFactory enabledLoggerFactory;
        if (config.logging().enabled()) {
            enabledLoggerFactory = this.loggerFactory != null
                ? this.loggerFactory
                : DefaultOperatonEngineBpmnLoggerFactory.INSTANCE;
        } else {
            enabledLoggerFactory = NoopOperatonEngineBpmnLoggerFactory.INSTANCE;
        }

        return new DefaultOperatonEngineBpmnTelemetry(config, traceEnabled, metricEnabled, tracer, meterRegistry, enabledLoggerFactory, enabledMetricsFactory);
    }
}
