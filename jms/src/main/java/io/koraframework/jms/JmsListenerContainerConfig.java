package io.koraframework.jms;

import io.koraframework.config.common.annotation.ConfigValueExtractor;
import io.koraframework.telemetry.common.TelemetryConfig;

@ConfigValueExtractor
public interface JmsListenerContainerConfig {
    String queueName();

    int threads();

    TelemetryConfig telemetry();
}
