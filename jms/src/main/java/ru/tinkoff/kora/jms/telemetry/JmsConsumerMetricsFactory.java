package ru.tinkoff.kora.jms.telemetry;

import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

public interface JmsConsumerMetricsFactory {
    JmsConsumerMetrics get(TelemetryConfig.MetricsConfig config, String queueName);
}
