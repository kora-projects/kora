package ru.tinkoff.kora.kafka.common.producer;

import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;

import jakarta.annotation.Nullable;
import java.time.Duration;
import java.util.Properties;

@ConfigValueExtractor
public interface KafkaPublisherConfig {

    Properties driverProperties();

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
