package io.koraframework.jms.telemetry;

public interface JmsConsumerTelemetryFactory {

    JmsConsumerTelemetry get(JmsConsumerTelemetryConfig config, String queueName);
}
