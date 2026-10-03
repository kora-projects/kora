package io.koraframework.nats.common.consumer.telemetry.impl;

import io.koraframework.nats.common.consumer.telemetry.NatsConsumerOperationObservation;
import io.koraframework.nats.common.consumer.telemetry.NatsConsumerPollObservation;
import io.koraframework.nats.common.consumer.telemetry.NatsConsumerTelemetry;

public final class NoopNatsConsumerTelemetry implements NatsConsumerTelemetry {
    public static final NoopNatsConsumerTelemetry INSTANCE = new NoopNatsConsumerTelemetry();

    @Override
    public NatsConsumerPollObservation observePoll() {
        return NoopNatsConsumerPollObservation.INSTANCE;
    }

    @Override
    public void reportPending(String subject, long messages, long bytes) {
    }

    @Override
    public void reportLag(String stream, String consumer, long pending) {
    }

    @Override
    public NatsConsumerOperationObservation observeOperation(String operation) {
        return NoopNatsConsumerOperationObservation.INSTANCE;
    }

}
