package io.koraframework.camunda.engine.bpmn.telemetry.impl;

import io.koraframework.camunda.engine.bpmn.CamundaEngineBpmnConfig;
import io.koraframework.camunda.engine.bpmn.telemetry.CamundaEngineBpmnTelemetry;
import io.koraframework.camunda.engine.bpmn.telemetry.CamundaEngineObservation;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;

import static io.opentelemetry.api.common.AttributeKey.stringKey;

public class DefaultCamundaEngineBpmnTelemetry implements CamundaEngineBpmnTelemetry {

    public record TelemetryContext(CamundaEngineBpmnConfig.CamundaTelemetryConfig config,
                                   boolean isTraceEnabled,
                                   boolean isMetricsEnabled,
                                   Tracer tracer,
                                   MeterRegistry meterRegistry) {}

    protected final TelemetryContext context;
    protected final DefaultCamundaEngineBpmnLoggerFactory loggerFactory;
    protected final DefaultCamundaEngineBpmnMetricsFactory metricsFactory;

    public DefaultCamundaEngineBpmnTelemetry(CamundaEngineBpmnConfig.CamundaTelemetryConfig config,
                                             boolean isTraceEnabled,
                                             boolean isMetricsEnabled,
                                             Tracer tracer,
                                             MeterRegistry meterRegistry,
                                             DefaultCamundaEngineBpmnLoggerFactory loggerFactory,
                                             DefaultCamundaEngineBpmnMetricsFactory metricsFactory) {
        this.context = new TelemetryContext(config, isTraceEnabled, isMetricsEnabled, tracer, meterRegistry);
        this.loggerFactory = loggerFactory;
        this.metricsFactory = metricsFactory;
    }

    @Override
    public CamundaEngineObservation observe(String javaDelegateName) {
        var span = this.createSpan(javaDelegateName);
        var logger = this.loggerFactory.create(this.context, javaDelegateName);
        var metrics = this.metricsFactory.create(this.context, javaDelegateName);

        return new DefaultCamundaEngineBpmnObservation(this.context, span, logger, metrics);
    }

    protected Span createSpan(String javaDelegateName) {
        if (!this.context.isTraceEnabled()) {
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
