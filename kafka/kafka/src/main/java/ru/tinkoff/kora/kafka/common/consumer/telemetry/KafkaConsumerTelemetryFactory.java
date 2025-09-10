package ru.tinkoff.kora.kafka.common.consumer.telemetry;

import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.util.Properties;

public interface KafkaConsumerTelemetryFactory<K, V> {

    KafkaConsumerTelemetry<K, V> get(String consumerName, Properties driverProperties, TelemetryConfig config);
}
