package io.koraframework.kafka.common.consumer.telemetry;

import io.koraframework.config.common.annotation.ConfigMapper;
import io.koraframework.telemetry.common.TelemetryConfig;

import java.util.Set;

@ConfigMapper
public interface KafkaConsumerTelemetryConfig extends TelemetryConfig {

    @Override
    KafkaConsumerLoggingConfig logging();

    @Override
    KafkaConsumerMetricsConfig metrics();

    @Override
    KafkaConsumerTracingConfig tracing();

    @ConfigMapper
    interface KafkaConsumerLoggingConfig extends LoggingConfig {
        default Set<String> maskHeaders() {
            return Set.of("authorization", "cookie", "set-cookie");
        }
    }

    @ConfigMapper
    interface KafkaConsumerMetricsConfig extends TelemetryConfig.MetricsConfig {
        default boolean driverMetrics() {
            return false;
        }
    }

    @ConfigMapper
    interface KafkaConsumerTracingConfig extends TelemetryConfig.TracingConfig { }
}
