package ru.tinkoff.kora.jms.telemetry;

public interface JmsConsumerLoggerFactory {
    JmsConsumerLogger get(String queueName);
}
