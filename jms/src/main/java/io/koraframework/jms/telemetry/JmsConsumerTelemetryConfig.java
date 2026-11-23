package io.koraframework.jms.telemetry;

import io.koraframework.common.util.Size;
import io.koraframework.config.common.annotation.ConfigMapper;
import io.koraframework.telemetry.common.TelemetryConfig;

import java.util.Set;

@ConfigMapper
public interface JmsConsumerTelemetryConfig extends TelemetryConfig {

    @Override
    JmsConsumerLoggingConfig logging();

    @Override
    JmsConsumerMetricsConfig metrics();

    @Override
    JmsConsumerTracingConfig tracing();

    @ConfigMapper
    interface JmsConsumerLoggingConfig extends TelemetryConfig.LoggingConfig {

        default boolean logBody() {
            return true;
        }

        default Size maxBodyLogSize() {
            return Size.of(2, Size.Type.MiB);
        }

        default Set<String> maskHeaders() {
            return Set.of("authorization", "cookie", "set-cookie", "password", "secret", "token");
        }

        default int maxLoggedProperties() {
            return 64;
        }

        default int maxPropertyValueLength() {
            return 1024;
        }

        default boolean stacktrace() {
            return true;
        }
    }

    @ConfigMapper
    interface JmsConsumerMetricsConfig extends TelemetryConfig.MetricsConfig {}

    @ConfigMapper
    interface JmsConsumerTracingConfig extends TelemetryConfig.TracingConfig {}
}
