package ru.tinkoff.kora.kafka.common.consumer.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

public interface KafkaConsumerLoggerFactory<K, V> {
    @Nullable
    KafkaConsumerLogger<K, V> get(TelemetryConfig.LogConfig logging);
}
