package io.koraframework.resilient.timeout.telemetry;

import io.koraframework.config.common.annotation.ConfigMapper;
import io.koraframework.telemetry.common.TelemetryConfig;

@ConfigMapper
public interface TimeoutTelemetryConfig extends TelemetryConfig {

    @Override
    TimeoutLoggingConfig logging();

    @Override
    TimeoutMetricsConfig metrics();

    @Override
    TimeoutTracingConfig tracing();

    @ConfigMapper
    interface TimeoutLoggingConfig extends TelemetryConfig.LoggingConfig {}

    @ConfigMapper
    interface TimeoutMetricsConfig extends TelemetryConfig.MetricsConfig {}

    @ConfigMapper
    interface TimeoutTracingConfig extends TelemetryConfig.TracingConfig {

        @Override
        default boolean enabled() {
            return false;
        }
    }
}
