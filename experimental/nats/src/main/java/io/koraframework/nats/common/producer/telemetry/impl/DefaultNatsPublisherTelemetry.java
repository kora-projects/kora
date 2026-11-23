package io.koraframework.nats.common.producer.telemetry.impl;

import io.koraframework.nats.common.producer.telemetry.NatsPublisherOperationObservation;
import io.koraframework.nats.common.producer.telemetry.NatsPublisherRecordObservation;
import io.koraframework.nats.common.producer.telemetry.NatsPublisherTelemetry;
import io.koraframework.nats.common.producer.telemetry.NatsPublisherTelemetryConfig;
import io.koraframework.nats.common.producer.telemetry.impl.DefaultNatsPublisherLoggerFactory.DefaultNatsPublisherLogger;
import io.koraframework.nats.common.producer.telemetry.impl.DefaultNatsPublisherMetricsFactory.DefaultNatsPublisherMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;

public class DefaultNatsPublisherTelemetry implements NatsPublisherTelemetry {
    public record TelemetryContext(String configPath, String canonicalName,
                                   NatsPublisherTelemetryConfig config, Tracer tracer, MeterRegistry registry,
                                   boolean tracing, boolean metrics) {
        public Tags tags() {
            var tags = Tags.of("config", configPath, "name", canonicalName, "role", "publisher");
            for (var entry : config.metrics().tags().entrySet()) {
                tags = tags.and(entry.getKey(), entry.getValue());
            }
            return tags;
        }
    }

    protected final TelemetryContext context;
    protected final DefaultNatsPublisherLogger logger;
    protected final DefaultNatsPublisherMetrics metrics;

    public DefaultNatsPublisherTelemetry(TelemetryContext context, DefaultNatsPublisherLogger logger, DefaultNatsPublisherMetrics metrics) {
        this.context = context;
        this.logger = logger;
        this.metrics = metrics;
    }

    @Override
    public NatsPublisherRecordObservation observeSend(String subject) {
        return record("publish", subject);
    }

    @Override
    public NatsPublisherRecordObservation observeRequest(String subject) {
        return record("request", subject);
    }

    protected NatsPublisherRecordObservation record(String operation, String subject) {
        metrics.count(operation, subject, 1);
        return new DefaultNatsPublisherRecordObservation(context, logger, metrics, start(operation, subject, SpanKind.PRODUCER, Context.current()), operation, subject);
    }

    protected Span start(String operation, String subject, SpanKind kind, Context parent) {
        if (!context.tracing()) {
            return Span.getInvalid();
        }
        var builder = context.tracer().spanBuilder(subject.isEmpty() ? operation : subject + " " + operation).setSpanKind(kind).setParent(parent)
            .setAttribute("messaging.system", "nats").setAttribute("messaging.destination.name", subject)
            .setAttribute("messaging.operation.name", operation).setAttribute("system.config", context.configPath())
            .setAttribute("system.name.canonical", context.canonicalName());
        context.config().tracing().attributes().forEach(builder::setAttribute);
        return builder.startSpan();
    }

    @Override
    public NatsPublisherOperationObservation observeOperation(String operation) {
        return new DefaultNatsPublisherOperationObservation(context, logger, metrics, start(operation, "", SpanKind.CLIENT, Context.current()), operation, "");
    }

}
