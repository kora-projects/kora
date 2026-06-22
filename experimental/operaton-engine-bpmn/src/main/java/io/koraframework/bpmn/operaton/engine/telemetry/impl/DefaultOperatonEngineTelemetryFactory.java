package io.koraframework.bpmn.operaton.engine.telemetry.impl;

import io.koraframework.bpmn.operaton.engine.telemetry.OperatonEngineTelemetry;
import io.koraframework.bpmn.operaton.engine.telemetry.OperatonEngineTelemetryConfig;
import io.koraframework.bpmn.operaton.engine.telemetry.OperatonEngineTelemetryFactory;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;
import org.jspecify.annotations.Nullable;

public class DefaultOperatonEngineTelemetryFactory implements OperatonEngineTelemetryFactory {

    public static final Tracer NOOP_TRACER = TracerProvider.noop().get("operaton-engine-bpmn");
    public static final MeterRegistry NOOP_METER_REGISTRY = new CompositeMeterRegistry();

    @Nullable
    private final Tracer tracer;
    @Nullable
    private final MeterRegistry meterRegistry;
    @Nullable
    private final DefaultOperatonEngineLoggerFactory loggerFactory;
    @Nullable
    private final DefaultOperatonEngineMetricsFactory metricsFactory;

    public DefaultOperatonEngineTelemetryFactory(@Nullable Tracer tracer,
                                                 @Nullable MeterRegistry meterRegistry,
                                                 @Nullable DefaultOperatonEngineLoggerFactory loggerFactory,
                                                 @Nullable DefaultOperatonEngineMetricsFactory metricsFactory) {
        this.tracer = tracer;
        this.meterRegistry = meterRegistry;
        this.loggerFactory = loggerFactory;
        this.metricsFactory = metricsFactory;
    }

    @Override
    public OperatonEngineTelemetry get(OperatonEngineTelemetryConfig config) {
        var traceEnabled = this.tracer != null && config.tracing().enabled();
        var metricEnabled = this.meterRegistry != null && config.metrics().enabled();
        if (!traceEnabled && !metricEnabled && !config.logging().enabled()) {
            return NoopOperatonEngineTelemetry.INSTANCE;
        }

        var tracer = traceEnabled ? this.tracer : NOOP_TRACER;
        var meterRegistry = metricEnabled ? this.meterRegistry : NOOP_METER_REGISTRY;

        final DefaultOperatonEngineMetricsFactory enabledMetricsFactory;
        if (metricEnabled) {
            enabledMetricsFactory = this.metricsFactory != null
                ? this.metricsFactory
                : DefaultOperatonEngineMetricsFactory.INSTANCE;
        } else {
            enabledMetricsFactory = NoopOperatonEngineMetricsFactory.INSTANCE;
        }

        final DefaultOperatonEngineLoggerFactory enabledLoggerFactory;
        if (config.logging().enabled()) {
            enabledLoggerFactory = this.loggerFactory != null
                ? this.loggerFactory
                : DefaultOperatonEngineLoggerFactory.INSTANCE;
        } else {
            enabledLoggerFactory = NoopOperatonEngineLoggerFactory.INSTANCE;
        }

        return build(config, tracer, meterRegistry, enabledMetricsFactory, enabledLoggerFactory);
    }

    protected OperatonEngineTelemetry build(OperatonEngineTelemetryConfig config,
                                           Tracer tracer,
                                           MeterRegistry meterRegistry,
                                           DefaultOperatonEngineMetricsFactory metricsFactory,
                                           DefaultOperatonEngineLoggerFactory loggerFactory) {
        return new DefaultOperatonEngineTelemetry(config, tracer, meterRegistry, metricsFactory, loggerFactory);
    }
}
