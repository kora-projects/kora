package ru.tinkoff.kora.kafka.common.producer;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.time.Duration;
import java.util.Properties;

@ConfigValueExtractor
public interface KafkaPublisherConfig {

    /**
     * @return Official Kafka Producer properties.
     */
    Properties driverProperties();

    /**
     * @return Telemetry configuration of the producer: logging, metrics and tracing.
     */
    TelemetryConfig telemetry();

    @ConfigValueExtractor
    interface TransactionConfig {

        /**
         * @return Transaction identifier prefix to which a random UUID is appended.
         */
        default String idPrefix() {
            return "kora-app-";
        }

        /**
         * @return Maximum size of the transactional Producer pool.
         */
        default int maxPoolSize() {
            return 10;
        }

        /**
         * @return Maximum time to wait for a free Producer from the pool.
         */
        default Duration maxWaitTime() {
            return Duration.ofSeconds(10);
        }
    }

    @ConfigValueExtractor
    interface TopicConfig {

        /**
         * @return Topic where the method sends data.
         */
        String topic();

        /**
         * @return Topic partition where the method sends data, standard Kafka partitioning is used when not specified.
         */
        @Nullable
        Integer partition();
    }
}
