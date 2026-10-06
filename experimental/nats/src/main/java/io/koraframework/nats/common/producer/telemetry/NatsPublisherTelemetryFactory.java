package io.koraframework.nats.common.producer.telemetry;

import java.util.Properties;

@FunctionalInterface
public interface NatsPublisherTelemetryFactory {
    NatsPublisherTelemetry get(String configPath, String canonicalName, NatsPublisherTelemetryConfig config, Properties driverProperties);
}
