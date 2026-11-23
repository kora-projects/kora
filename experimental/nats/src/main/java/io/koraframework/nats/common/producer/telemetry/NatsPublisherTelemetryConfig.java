package io.koraframework.nats.common.producer.telemetry;

import io.koraframework.config.common.annotation.ConfigMapper;
import io.koraframework.telemetry.common.TelemetryConfig;

import java.util.Set;

@ConfigMapper
public interface NatsPublisherTelemetryConfig extends TelemetryConfig {
    @Override
    NatsPublisherLoggingConfig logging();

    @Override
    NatsPublisherMetricsConfig metrics();

    @Override
    NatsPublisherTracingConfig tracing();

    @ConfigMapper
    interface NatsPublisherLoggingConfig extends LoggingConfig {
        default Set<String> maskHeaders() {
            return Set.of("authorization", "cookie", "set-cookie");
        }

        default boolean logBody() {
            return false;
        }
    }

    @ConfigMapper
    interface NatsPublisherMetricsConfig extends MetricsConfig {
    }

    @ConfigMapper
    interface NatsPublisherTracingConfig extends TracingConfig {
    }
}
