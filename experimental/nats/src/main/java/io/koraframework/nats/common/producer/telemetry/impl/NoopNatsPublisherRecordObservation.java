package io.koraframework.nats.common.producer.telemetry.impl;

import io.koraframework.nats.common.producer.telemetry.NatsPublisherRecordObservation;
import io.nats.client.Message;
import io.opentelemetry.api.trace.Span;
import org.jspecify.annotations.Nullable;

public final class NoopNatsPublisherRecordObservation implements NatsPublisherRecordObservation {
    public static final NoopNatsPublisherRecordObservation INSTANCE = new NoopNatsPublisherRecordObservation();

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
