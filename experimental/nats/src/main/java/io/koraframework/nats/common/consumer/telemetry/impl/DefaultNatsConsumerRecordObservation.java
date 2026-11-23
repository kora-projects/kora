package io.koraframework.nats.common.consumer.telemetry.impl;

import io.koraframework.nats.common.consumer.deserializer.NatsDeserializer;
import io.koraframework.nats.common.consumer.telemetry.NatsConsumerOperationObservation;
import io.koraframework.nats.common.consumer.telemetry.NatsConsumerRecordObservation;
import io.koraframework.nats.common.consumer.telemetry.NatsConsumerReplyObservation;
import io.koraframework.nats.common.consumer.telemetry.impl.DefaultNatsConsumerLoggerFactory.DefaultNatsConsumerLogger;
import io.koraframework.nats.common.consumer.telemetry.impl.DefaultNatsConsumerMetricsFactory.DefaultNatsConsumerMetrics;
import io.koraframework.nats.common.consumer.telemetry.impl.DefaultNatsConsumerTelemetry.TelemetryContext;
import io.nats.client.Message;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import org.jspecify.annotations.Nullable;

public class DefaultNatsConsumerRecordObservation extends DefaultNatsConsumerOperationObservation implements NatsConsumerRecordObservation {
    private final Message message;
    private @Nullable NatsDeserializer<?> deserializer;

    public DefaultNatsConsumerRecordObservation(TelemetryContext context, DefaultNatsConsumerLogger logger, DefaultNatsConsumerMetrics metrics, Span span, Message message) {
        super(context, logger, metrics, span, "process", message.getSubject());
        this.message = message;
        if (message.getData() != null) {
            span.setAttribute("messaging.message.body.size", message.getData().length);
        }
        if (message.isJetStream() && message.metaData() != null) {
            var metadata = message.metaData();
            span.setAttribute("messaging.nats.stream", metadata.getStream());
            span.setAttribute("messaging.nats.consumer", metadata.getConsumer());
            span.setAttribute("messaging.nats.sequence", metadata.streamSequence());
            span.setAttribute("messaging.nats.delivered", metadata.deliveredCount());
        }
    }

    @Override
    public void observeDeserializer(NatsDeserializer<?> deserializer) {
        this.deserializer = deserializer;
    }

    @Override
    public void observeHandle() {
        span.addEvent("handle");
        if (deserializer != null) {
            logger.record(message, deserializer);
        }
    }

    @Override
    public void observeData(@Nullable Object value) {
        if (deserializer == null) {
            logger.record(message, value);
        }
    }

    @Override
    public NatsConsumerOperationObservation observeAck(String acknowledgement) {
        var ackSpan = context.tracing() ? context.tracer().spanBuilder(acknowledgement).setSpanKind(SpanKind.CLIENT)
            .setParent(Context.root().with(span)).setAttribute("messaging.system", "nats")
            .setAttribute("messaging.destination.name", subject).startSpan() : Span.getInvalid();
        return new DefaultNatsConsumerOperationObservation(context, logger, metrics, ackSpan, acknowledgement, subject);
    }

    @Override
    public NatsConsumerReplyObservation observeReply(String replySubject) {
        var replySpan = context.tracing() ? context.tracer().spanBuilder(replySubject + " reply").setSpanKind(SpanKind.PRODUCER)
            .setParent(Context.root().with(span)).setAttribute("messaging.system", "nats")
            .setAttribute("messaging.destination.name", replySubject).startSpan() : Span.getInvalid();
        return new DefaultNatsConsumerReplyObservation(context, logger, metrics, replySpan, "reply", replySubject);
    }
}
