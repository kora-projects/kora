package ru.tinkoff.kora.jms.telemetry;

import javax.jms.JMSException;
import javax.jms.Message;

public interface JmsConsumerTracer {
    JmsConsumerSpan get(Message message) throws JMSException;

    interface JmsConsumerSpan {
        void close(long duration, Exception e);
    }
}
