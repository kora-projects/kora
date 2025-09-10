package ru.tinkoff.kora.kafka.common.producer.telemetry;

import jakarta.annotation.Nullable;
import org.apache.kafka.clients.producer.Producer;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.util.Properties;

public interface KafkaProducerTelemetryFactory {

    @Nullable
    KafkaProducerTelemetry get(String producerName, TelemetryConfig config, Producer<?, ?> producer, Properties properties);
}
