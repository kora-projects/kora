package io.koraframework.kafka.common.producer.telemetry;

import io.koraframework.config.common.annotation.ConfigValueExtractor;
import io.koraframework.telemetry.common.TelemetryConfig;

@ConfigValueExtractor
public interface KafkaPublisherTelemetryConfig extends TelemetryConfig {
    @Override
    KafkaProducerLoggingConfig logging();

    @Override
    KafkaProducerMetricsConfig metrics();

    @Override
    KafkaProducerTracingConfig tracing();

    @ConfigValueExtractor
    interface KafkaProducerLoggingConfig extends LogConfig {
    }

    @ConfigValueExtractor
    interface KafkaProducerTracingConfig extends TracingConfig {
    }

    @ConfigValueExtractor
    interface KafkaProducerMetricsConfig extends MetricsConfig {
        default boolean driverMetrics() {
            return false;
        }
    }
}
