package io.koraframework.jms;

import io.koraframework.config.common.annotation.ConfigMapper;
import io.koraframework.jms.telemetry.JmsConsumerTelemetryConfig;

@ConfigMapper
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
    JmsConsumerTelemetryConfig telemetry();
}
