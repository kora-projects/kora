package io.koraframework.jms;

import io.koraframework.config.common.annotation.ConfigMapper;
import io.koraframework.jms.telemetry.JmsConsumerTelemetryConfig;

@ConfigMapper
public interface JmsListenerContainerConfig {
    String queueName();

    int threads();

    JmsConsumerTelemetryConfig telemetry();
}
