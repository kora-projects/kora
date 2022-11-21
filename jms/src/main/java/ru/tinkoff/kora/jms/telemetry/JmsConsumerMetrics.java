package ru.tinkoff.kora.jms.telemetry;

import javax.jms.Message;

public interface JmsConsumerMetrics {
    void onMessageProcessed(Message message, long duration);
}
