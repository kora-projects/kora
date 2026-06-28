package io.koraframework.camunda.engine.bpmn.telemetry.impl;

import io.koraframework.camunda.engine.bpmn.telemetry.CamundaEngineTelemetryConfig;
import io.koraframework.camunda.engine.bpmn.telemetry.CamundaEngineTelemetry;
import io.koraframework.camunda.engine.bpmn.telemetry.CamundaEngineObservation;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;

import static io.opentelemetry.api.common.AttributeKey.stringKey;

public class DefaultCamundaEngineTelemetry implements CamundaEngineTelemetry {

    public record TelemetryContext(CamundaEngineTelemetryConfig config,
                                   boolean isTracingEnabled,
                                   boolean isMetricsEnabled,
                                   Tracer tracer,
                                   MeterRegistry meterRegistry) {}

    protected final TelemetryContext context;
    protected final DefaultCamundaEngineLoggerFactory loggerFactory;
    protected final DefaultCamundaEngineMetricsFactory metricsFactory;

    public DefaultCamundaEngineTelemetry(CamundaEngineTelemetryConfig config,
                                         Tracer tracer,
                                         MeterRegistry meterRegistry,
                                         DefaultCamundaEngineMetricsFactory metricsFactory,
                                         DefaultCamundaEngineLoggerFactory loggerFactory) {
        var isTracingEnabled = config.tracing().enabled() && tracer != DefaultCamundaEngineTelemetryFactory.NOOP_TRACER;
        var isMetricsEnabled = config.metrics().enabled() && meterRegistry != DefaultCamundaEngineTelemetryFactory.NOOP_METER_REGISTRY;
        this.context = new TelemetryContext(config, isTracingEnabled, isMetricsEnabled, tracer, meterRegistry);
        this.loggerFactory = loggerFactory;
        this.metricsFactory = metricsFactory;
    }

    @Override
    public CamundaEngineObservation observe(String javaDelegateName) {
        var span = this.createSpan(javaDelegateName);
        var logger = this.loggerFactory.create(this.context, javaDelegateName);
        var metrics = this.metricsFactory.create(this.context, javaDelegateName);

        return new DefaultCamundaEngineObservation(this.context, span, logger, metrics);
    }

    protected Span createSpan(String javaDelegateName) {
        if (!this.context.isTracingEnabled()) {
            return Span.getInvalid();
        }

        var span = this.context.tracer()
            .spanBuilder("Camunda Delegate " + javaDelegateName)
            .setSpanKind(SpanKind.INTERNAL)
            .setParent(io.opentelemetry.context.Context.current())
            .setAttribute("delegate", javaDelegateName);
        for (var entry : this.context.config().tracing().attributes().entrySet()) {
            span.setAttribute(entry.getKey(), entry.getValue());
        }
        return span.startSpan();
    }

    protected static SpanBuilder setNotNull(SpanBuilder builder, String name, String value) {
        if (value != null) {
            builder.setAttribute(stringKey(name), value);
        }
        return builder;
    }
}
