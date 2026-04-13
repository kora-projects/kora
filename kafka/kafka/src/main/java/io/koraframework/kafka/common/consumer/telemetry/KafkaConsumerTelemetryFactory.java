package io.koraframework.kafka.common.consumer.telemetry;

import java.util.Properties;

public interface KafkaConsumerTelemetryFactory {

    KafkaConsumerTelemetry get(String listenerName, String listenerImpl, Properties driverProperties, KafkaConsumerTelemetryConfig config);
}
