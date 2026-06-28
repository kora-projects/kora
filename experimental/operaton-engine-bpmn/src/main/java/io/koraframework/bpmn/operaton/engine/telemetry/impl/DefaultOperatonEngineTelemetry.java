package io.koraframework.bpmn.operaton.engine.telemetry.impl;

import io.koraframework.bpmn.operaton.engine.telemetry.OperatonEngineObservation;
import io.koraframework.bpmn.operaton.engine.telemetry.OperatonEngineTelemetry;
import io.koraframework.bpmn.operaton.engine.telemetry.OperatonEngineTelemetryConfig;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;

import static io.opentelemetry.api.common.AttributeKey.stringKey;

public class DefaultOperatonEngineTelemetry implements OperatonEngineTelemetry {

    public record TelemetryContext(OperatonEngineTelemetryConfig config,
                                   boolean isTraceEnabled,
                                   boolean isMetricsEnabled,
                                   Tracer tracer,
                                   MeterRegistry meterRegistry) {}

    protected final TelemetryContext context;
    protected final DefaultOperatonEngineLoggerFactory loggerFactory;
    protected final DefaultOperatonEngineMetricsFactory metricsFactory;

    public DefaultOperatonEngineTelemetry(OperatonEngineTelemetryConfig config,
                                          Tracer tracer,
                                          MeterRegistry meterRegistry,
                                          DefaultOperatonEngineMetricsFactory metricsFactory,
                                          DefaultOperatonEngineLoggerFactory loggerFactory) {
        var isTraceEnabled = config.tracing().enabled() && tracer != DefaultOperatonEngineTelemetryFactory.NOOP_TRACER;
        var isMetricsEnabled = config.metrics().enabled() && meterRegistry != DefaultOperatonEngineTelemetryFactory.NOOP_METER_REGISTRY;
        this.context = new TelemetryContext(config, isTraceEnabled, isMetricsEnabled, tracer, meterRegistry);
        this.loggerFactory = loggerFactory;
        this.metricsFactory = metricsFactory;
    }

    @Override
    public OperatonEngineObservation observe(String javaDelegateName) {
        var span = this.createSpan(javaDelegateName);
        var logger = this.loggerFactory.create(this.context, javaDelegateName);
        var metrics = this.metricsFactory.create(this.context, javaDelegateName);

        return new DefaultOperatonEngineObservation(this.context, span, logger, metrics);
    }

    protected Span createSpan(String javaDelegateName) {
        if (!this.context.isTraceEnabled()) {
            return Span.getInvalid();
        }

        var span = this.context.tracer()
            .spanBuilder("Operaton Delegate " + javaDelegateName)
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
