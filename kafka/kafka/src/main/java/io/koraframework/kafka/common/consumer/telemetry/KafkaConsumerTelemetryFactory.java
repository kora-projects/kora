package io.koraframework.kafka.common.consumer.telemetry;

import java.util.Properties;

public interface KafkaConsumerTelemetryFactory {

    KafkaConsumerTelemetry get(String listenerConfig, String listenerCanonicalName, Properties driverProperties, KafkaConsumerTelemetryConfig config);
}
