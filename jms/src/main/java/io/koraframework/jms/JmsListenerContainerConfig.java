package io.koraframework.jms;

import io.koraframework.config.common.annotation.ConfigValueExtractor;
import io.koraframework.jms.telemetry.JmsConsumerTelemetryConfig;

@ConfigValueExtractor
public interface JmsListenerContainerConfig {
    String queueName();

    int threads();

    JmsConsumerTelemetryConfig telemetry();
}
