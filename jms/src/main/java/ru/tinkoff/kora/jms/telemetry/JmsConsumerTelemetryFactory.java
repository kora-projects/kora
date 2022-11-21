package ru.tinkoff.kora.jms.telemetry;

public interface JmsConsumerTelemetryFactory {
    JmsConsumerTelemetry get(String queueName);
}
