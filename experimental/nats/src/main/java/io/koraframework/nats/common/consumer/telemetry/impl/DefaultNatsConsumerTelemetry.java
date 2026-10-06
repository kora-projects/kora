package io.koraframework.nats.common.consumer.telemetry.impl;

import io.koraframework.nats.common.consumer.telemetry.*;
import io.koraframework.nats.common.consumer.telemetry.impl.DefaultNatsConsumerLoggerFactory.DefaultNatsConsumerLogger;
import io.koraframework.nats.common.consumer.telemetry.impl.DefaultNatsConsumerMetricsFactory.DefaultNatsConsumerMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.nats.client.Message;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import org.jspecify.annotations.Nullable;

import java.util.List;

public class DefaultNatsConsumerTelemetry implements NatsConsumerTelemetry {
    public record TelemetryContext(String configPath, String canonicalName,
                                   NatsConsumerTelemetryConfig config, Tracer tracer, MeterRegistry registry,
                                   boolean tracing, boolean metrics) {
        public Tags tags() {
            var tags = Tags.of("config", configPath, "name", canonicalName, "role", "consumer");
            for (var entry : config.metrics().tags().entrySet()) {
                tags = tags.and(entry.getKey(), entry.getValue());
            }
            return tags;
        }
    }

    protected final TelemetryContext context;
    protected final DefaultNatsConsumerLogger logger;
    protected final DefaultNatsConsumerMetrics metrics;

    public DefaultNatsConsumerTelemetry(TelemetryContext context, DefaultNatsConsumerLogger logger, DefaultNatsConsumerMetrics metrics) {
        this.context = context;
        this.logger = logger;
        this.metrics = metrics;
    }

    @Override
    public NatsConsumerPollObservation observePoll() {
        var span = start("poll", "", SpanKind.CONSUMER, Context.root());
        return new DefaultNatsConsumerPollObservation(context, logger, metrics, span, this);
    }

    public NatsConsumerRecordObservation record(Message message) {
        var parent = context.tracing() ? W3CTraceContextPropagator.getInstance().extract(Context.root(), message, MessageGetter.INSTANCE) : Context.root();
        return new DefaultNatsConsumerRecordObservation(context, logger, metrics, start("process", message.getSubject(), SpanKind.CONSUMER, parent), message);
    }

    @Override
    public void reportPending(String subject, long messages, long bytes) {
        metrics.pending("pending.messages", subject, messages);
        metrics.pending("pending.bytes", subject, bytes);
    }

    @Override
    public void reportLag(String stream, String consumer, long pending) {
        metrics.pending("lag", stream + "/" + consumer, pending);
    }

    private enum MessageGetter implements TextMapGetter<Message> {
        INSTANCE;

        @Override
        public Iterable<String> keys(Message carrier) {
            return carrier.hasHeaders() ? carrier.getHeaders().keySet() : List.of();
        }

        @Override
        public @Nullable String get(@Nullable Message carrier, String key) {
            if (carrier == null || !carrier.hasHeaders()) {
                return null;
            }
            var values = carrier.getHeaders().getIgnoreCase(key);
            return values == null || values.isEmpty() ? null : values.getFirst();
        }
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
    public NatsConsumerOperationObservation observeOperation(String operation) {
        return new DefaultNatsConsumerOperationObservation(context, logger, metrics, start(operation, "", SpanKind.CLIENT, Context.current()), operation, "");
    }

    @Override
    public void close() {
        metrics.close();
    }
}
