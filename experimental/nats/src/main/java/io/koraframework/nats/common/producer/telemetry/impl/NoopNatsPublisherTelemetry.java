package io.koraframework.nats.common.producer.telemetry.impl;

import io.koraframework.nats.common.producer.telemetry.NatsPublisherOperationObservation;
import io.koraframework.nats.common.producer.telemetry.NatsPublisherRecordObservation;
import io.koraframework.nats.common.producer.telemetry.NatsPublisherTelemetry;

public final class NoopNatsPublisherTelemetry implements NatsPublisherTelemetry {
    public static final NoopNatsPublisherTelemetry INSTANCE = new NoopNatsPublisherTelemetry();

    @Override
    public NatsPublisherRecordObservation observeSend(String subject) {
        return NoopNatsPublisherRecordObservation.INSTANCE;
    }

    @Override
    public NatsPublisherRecordObservation observeRequest(String subject) {
        return NoopNatsPublisherRecordObservation.INSTANCE;
    }

    @Override
    public NatsPublisherOperationObservation observeOperation(String operation) {
        return NoopNatsPublisherOperationObservation.INSTANCE;
    }

}
