package io.koraframework.resilient.timeout.telemetry;

import io.koraframework.config.common.annotation.ConfigValueExtractor;
import io.koraframework.telemetry.common.TelemetryConfig;

@ConfigValueExtractor
public interface TimeoutTelemetryConfig extends TelemetryConfig {

    @Override
    TimeoutLoggingConfig logging();

    @Override
    TimeoutMetricsConfig metrics();

    @Override
    TimeoutTracingConfig tracing();

    @ConfigValueExtractor
    interface TimeoutLoggingConfig extends TelemetryConfig.LoggingConfig {}

    @ConfigValueExtractor
    interface TimeoutMetricsConfig extends TelemetryConfig.MetricsConfig {}

    @ConfigValueExtractor
    interface TimeoutTracingConfig extends TelemetryConfig.TracingConfig {

        @Override
        default boolean enabled() {
            return false;
        }
    }
}
