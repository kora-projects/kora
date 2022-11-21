package ru.tinkoff.kora.jms.telemetry;

public interface JmsConsumerMetricsFactory {
    JmsConsumerMetrics get(String queueName);
}
