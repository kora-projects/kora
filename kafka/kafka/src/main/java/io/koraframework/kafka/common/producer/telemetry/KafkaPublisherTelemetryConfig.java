package io.koraframework.kafka.common.producer.telemetry;

import io.koraframework.config.common.annotation.ConfigMapper;
import io.koraframework.telemetry.common.TelemetryConfig;

@ConfigMapper
public interface KafkaPublisherTelemetryConfig extends TelemetryConfig {

    @Override
    KafkaProducerLoggingConfig logging();

    @Override
    KafkaProducerMetricsConfig metrics();

    @Override
    KafkaProducerTracingConfig tracing();

    @ConfigMapper
    interface KafkaProducerLoggingConfig extends LoggingConfig {
    }

    @ConfigMapper
    interface KafkaProducerTracingConfig extends TracingConfig {
    }

    @ConfigMapper
    interface KafkaProducerMetricsConfig extends MetricsConfig {
        default boolean driverMetrics() {
            return false;
        }
    }
}
