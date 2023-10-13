package ru.tinkoff.kora.jms;

import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

@ConfigValueExtractor
public interface JmsListenerContainerConfig {
    String queueName();

    int threads();

    TelemetryConfig telemetry();
}
