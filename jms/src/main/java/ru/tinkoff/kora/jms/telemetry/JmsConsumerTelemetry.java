package ru.tinkoff.kora.jms.telemetry;

import javax.jms.JMSException;
import javax.jms.Message;

public interface JmsConsumerTelemetry {
    JmsConsumerObservation observe(Message message) throws JMSException;

}
