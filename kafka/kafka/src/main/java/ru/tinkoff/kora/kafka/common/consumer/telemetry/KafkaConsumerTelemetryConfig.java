package ru.tinkoff.kora.kafka.common.consumer.telemetry;

import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

@ConfigValueExtractor
public interface KafkaConsumerTelemetryConfig extends TelemetryConfig {
    @Override
    KafkaConsumerLoggingConfig logging();

    @Override
    KafkaConsumerMetricsConfig metrics();

    @Override
    KafkaConsumerTracingConfig tracing();

    @ConfigValueExtractor
    interface KafkaConsumerLoggingConfig extends TelemetryConfig.LogConfig {
    }

    @ConfigValueExtractor
    interface KafkaConsumerMetricsConfig extends TelemetryConfig.MetricsConfig {
        default boolean driverMetrics() {
            return false;
        }
    }

    @ConfigValueExtractor
    interface KafkaConsumerTracingConfig extends TelemetryConfig.TracingConfig {
    }


}
