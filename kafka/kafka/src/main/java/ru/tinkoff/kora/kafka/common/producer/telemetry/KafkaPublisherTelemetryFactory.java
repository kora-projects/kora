package ru.tinkoff.kora.kafka.common.producer.telemetry;

import org.jspecify.annotations.Nullable;

import java.util.Properties;

public interface KafkaPublisherTelemetryFactory {

    @Nullable
    KafkaPublisherTelemetry get(String producerName, KafkaPublisherTelemetryConfig config, Properties properties);
}
