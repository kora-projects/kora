package ru.tinkoff.kora.jms.telemetry;

import javax.jms.Message;

public interface JmsConsumerLogger {
    void onMessageReceived(Message message);

    void onMessageProcessed(Message message, long duration);
}
