package io.koraframework.nats.common.consumer.telemetry.impl;

import io.koraframework.nats.common.consumer.telemetry.NatsConsumerOperationObservation;
import io.koraframework.nats.common.consumer.telemetry.NatsConsumerRecordObservation;
import io.koraframework.nats.common.consumer.telemetry.NatsConsumerReplyObservation;
import io.opentelemetry.api.trace.Span;
import org.jspecify.annotations.Nullable;

public final class NoopNatsConsumerRecordObservation implements NatsConsumerRecordObservation {
    public static final NoopNatsConsumerRecordObservation INSTANCE = new NoopNatsConsumerRecordObservation();

    @Override
    public Span span() {
        return Span.getInvalid();
    }

    @Override
    public void end() {
    }

    @Override
    public void observeError(Throwable error) {
    }

    @Override
    public void observeHandle() {
    }

    @Override
    public void observeData(@Nullable Object value) {
    }

    @Override
    public NatsConsumerOperationObservation observeAck(String acknowledgement) {
        return NoopNatsConsumerOperationObservation.INSTANCE;
    }

    @Override
    public NatsConsumerReplyObservation observeReply(String subject) {
        return NoopNatsConsumerReplyObservation.INSTANCE;
    }
}
