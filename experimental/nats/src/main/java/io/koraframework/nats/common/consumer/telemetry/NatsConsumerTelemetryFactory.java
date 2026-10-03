package io.koraframework.nats.common.consumer.telemetry;

import java.util.Properties;

@FunctionalInterface
public interface NatsConsumerTelemetryFactory {
    NatsConsumerTelemetry get(String configPath, String canonicalName, NatsConsumerTelemetryConfig config, Properties driverProperties);
}
