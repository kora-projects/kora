package ru.tinkoff.kora.kafka.common.producer.telemetry;

import jakarta.annotation.Nullable;
import org.apache.kafka.clients.producer.Producer;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.util.Properties;

public interface KafkaProducerLoggerFactory {
    @Nullable
    KafkaProducerLogger get(TelemetryConfig.LogConfig logging, Producer<?, ?> producer, Properties properties);
}
