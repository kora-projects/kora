package io.koraframework.resilient.fallback.telemetry;

import io.koraframework.config.common.annotation.ConfigMapper;
import io.koraframework.telemetry.common.TelemetryConfig;

@ConfigMapper
public interface FallbackTelemetryConfig extends TelemetryConfig {

    @Override
    FallbackLoggingConfig logging();

    @Override
    FallbackMetricsConfig metrics();

    @Override
    FallbackTracingConfig tracing();

    @ConfigMapper
    interface FallbackLoggingConfig extends TelemetryConfig.LoggingConfig {}

    @ConfigMapper
    interface FallbackMetricsConfig extends TelemetryConfig.MetricsConfig {}

    @ConfigMapper
    interface FallbackTracingConfig extends TelemetryConfig.TracingConfig {

        @Override
        default boolean enabled() {
            return false;
        }
    }
}
