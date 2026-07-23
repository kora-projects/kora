package ru.tinkoff.kora.jms;

import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

@ConfigValueExtractor
public interface JmsListenerContainerConfig {
    /**
     * @return Name of the JMS queue the listener consumes messages from.
     */
    String queueName();

    /**
     * @return Number of consumer threads listening to the queue, zero disables the listener.
     */
    int threads();

    /**
     * @return Telemetry configuration for logging, metrics and tracing of consumed messages.
     */
    TelemetryConfig telemetry();
}
