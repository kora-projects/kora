package io.koraframework.kafka.common.producer.telemetry;

import org.jspecify.annotations.Nullable;

import java.util.Properties;

public interface KafkaPublisherTelemetryFactory {

    @Nullable
    KafkaPublisherTelemetry get(String publisherName, String publisherImpl, KafkaPublisherTelemetryConfig config, Properties properties);
}
