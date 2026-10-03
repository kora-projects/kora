package io.koraframework.nats.common.consumer.telemetry;

import io.koraframework.config.common.annotation.ConfigMapper;
import io.koraframework.telemetry.common.TelemetryConfig;

import java.util.Set;

@ConfigMapper
public interface NatsConsumerTelemetryConfig extends TelemetryConfig {
    @Override
    NatsConsumerLoggingConfig logging();

    @Override
    NatsConsumerMetricsConfig metrics();

    @Override
    NatsConsumerTracingConfig tracing();

    @ConfigMapper
    interface NatsConsumerLoggingConfig extends LoggingConfig {
        default Set<String> maskHeaders() {
            return Set.of("authorization", "cookie", "set-cookie");
        }

        default boolean logBody() {
            return false;
        }
    }

    @ConfigMapper
    interface NatsConsumerMetricsConfig extends MetricsConfig {
    }

    @ConfigMapper
    interface NatsConsumerTracingConfig extends TracingConfig {
    }
}
