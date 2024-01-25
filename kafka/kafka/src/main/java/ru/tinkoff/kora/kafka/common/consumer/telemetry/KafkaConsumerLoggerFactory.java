package ru.tinkoff.kora.kafka.common.consumer.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.util.Properties;

public interface KafkaConsumerLoggerFactory<K, V> {
    @Nullable
    KafkaConsumerLogger<K, V> get(Properties driverProperties, TelemetryConfig.LogConfig logging);
}
