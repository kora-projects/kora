package io.koraframework.nats.common.consumer.telemetry.impl;

import io.koraframework.nats.common.consumer.NatsMessages;
import io.koraframework.nats.common.consumer.telemetry.NatsConsumerPollObservation;
import io.koraframework.nats.common.consumer.telemetry.NatsConsumerRecordObservation;
import io.nats.client.Message;
import io.opentelemetry.api.trace.Span;

public final class NoopNatsConsumerPollObservation implements NatsConsumerPollObservation {
    public static final NoopNatsConsumerPollObservation INSTANCE = new NoopNatsConsumerPollObservation();

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
    public void observeRecords(NatsMessages<?> records) {
    }

    @Override
    public NatsConsumerRecordObservation observeRecord(Message message) {
        return NoopNatsConsumerRecordObservation.INSTANCE;
    }
}
