package io.koraframework.jms.telemetry.impl;

import io.koraframework.jms.telemetry.JmsConsumerObservation;
import io.koraframework.jms.telemetry.JmsConsumerTelemetry;

import javax.jms.Message;

public final class NoopJmsConsumerTelemetry implements JmsConsumerTelemetry {

    public static final NoopJmsConsumerTelemetry INSTANCE = new NoopJmsConsumerTelemetry();

    private NoopJmsConsumerTelemetry() {}

    @Override
    public JmsConsumerObservation observe(Message message) {
        return NoopJmsConsumerObservation.INSTANCE;
    }
}
