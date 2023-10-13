package ru.tinkoff.kora.jms.telemetry;

import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

public interface JmsConsumerTelemetryFactory {
    JmsConsumerTelemetry get(TelemetryConfig config, String queueName);
}
