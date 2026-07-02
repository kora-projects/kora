package io.koraframework.kafka.common.producer;

import org.jspecify.annotations.Nullable;
import io.koraframework.config.common.annotation.ConfigMapper;
import io.koraframework.kafka.common.producer.telemetry.KafkaPublisherTelemetryConfig;

import java.time.Duration;
import java.util.Properties;

@ConfigMapper
public interface KafkaPublisherConfig {

    Properties driverProperties();

    KafkaPublisherTelemetryConfig telemetry();

    @ConfigMapper
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

    @ConfigMapper
    interface TopicConfig {

        String topic();

        @Nullable
        Integer partition();
    }
}
