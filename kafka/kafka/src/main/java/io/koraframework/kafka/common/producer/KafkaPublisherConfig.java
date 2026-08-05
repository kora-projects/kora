package io.koraframework.kafka.common.producer;

import org.jspecify.annotations.Nullable;
import io.koraframework.config.common.annotation.ConfigMapper;
import io.koraframework.kafka.common.producer.telemetry.KafkaPublisherTelemetryConfig;

import java.time.Duration;
import java.util.Properties;

@ConfigMapper
public interface KafkaPublisherConfig {

    /**
     * @return Official Kafka Producer properties.
     */
    Properties driverProperties();

    /**
     * @return Telemetry configuration of the producer: logging, metrics and tracing.
     */
    KafkaPublisherTelemetryConfig telemetry();

    @ConfigMapper
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

    @ConfigMapper
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
