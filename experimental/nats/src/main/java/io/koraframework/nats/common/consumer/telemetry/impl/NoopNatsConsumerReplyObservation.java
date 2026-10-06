package io.koraframework.nats.common.consumer.telemetry.impl;

import io.koraframework.nats.common.consumer.telemetry.NatsConsumerReplyObservation;
import io.nats.client.Message;
import io.opentelemetry.api.trace.Span;
import org.jspecify.annotations.Nullable;

public final class NoopNatsConsumerReplyObservation implements NatsConsumerReplyObservation {
    public static final NoopNatsConsumerReplyObservation INSTANCE = new NoopNatsConsumerReplyObservation();

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
    public void observeData(@Nullable Object value) {
    }

    @Override
    public void observeRecord(Message message) {
    }
}
