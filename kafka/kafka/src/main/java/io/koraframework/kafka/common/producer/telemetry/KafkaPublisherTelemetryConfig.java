package io.koraframework.kafka.common.producer.telemetry;

import io.koraframework.config.common.annotation.ConfigMapper;
import io.koraframework.telemetry.common.TelemetryConfig;

import java.util.Set;

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
        default Set<String> maskHeaders() {
            return Set.of("authorization", "cookie", "set-cookie");
        }
    }

    @ConfigMapper
    interface KafkaProducerMetricsConfig extends MetricsConfig {
        default boolean driverMetrics() {
            return false;
        }
    }

    @ConfigMapper
    interface KafkaProducerTracingConfig extends TracingConfig { }
}
