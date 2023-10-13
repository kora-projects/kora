package ru.tinkoff.kora.jms.telemetry;

import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

public interface JmsConsumerLoggerFactory {
    JmsConsumerLogger get(TelemetryConfig.LogConfig config, String queueName);
}
