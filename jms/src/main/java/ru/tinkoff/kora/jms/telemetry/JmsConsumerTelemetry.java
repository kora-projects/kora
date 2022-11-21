package ru.tinkoff.kora.jms.telemetry;

import javax.jms.JMSException;
import javax.jms.Message;

public interface JmsConsumerTelemetry {
    JmsConsumerTelemetryContext get(Message message) throws JMSException;

    interface JmsConsumerTelemetryContext {
        void close(Exception e);
    }
}
