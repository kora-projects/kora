package ru.tinkoff.kora.kafka.common.producer;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;
import ru.tinkoff.kora.kafka.common.producer.telemetry.KafkaPublisherTelemetryConfig;

import java.time.Duration;
import java.util.Properties;

@ConfigValueExtractor
public interface KafkaPublisherConfig {

    Properties driverProperties();

    KafkaPublisherTelemetryConfig telemetry();

    @ConfigValueExtractor
    interface TransactionConfig {

        default String idPrefix() {
            return "kora-app-";
        }

        default int maxPoolSize() {
            return 10;
        }

        default Duration maxWaitTime() {
            return Duration.ofSeconds(10);
        }
    }

    @ConfigValueExtractor
    interface TopicConfig {

        String topic();

        @Nullable
        Integer partition();
    }
}
