package io.koraframework.jms.telemetry;

import io.koraframework.telemetry.common.TelemetryConfig;

public interface JmsConsumerTelemetryFactory {
    JmsConsumerTelemetry get(TelemetryConfig config, String queueName);
}
