package io.koraframework.resilient.fallback.telemetry;

import io.koraframework.config.common.annotation.ConfigValueExtractor;
import io.koraframework.telemetry.common.TelemetryConfig;

@ConfigValueExtractor
public interface FallbackTelemetryConfig extends TelemetryConfig {

    @Override
    FallbackLoggingConfig logging();

    @Override
    FallbackMetricsConfig metrics();

    @Override
    FallbackTracingConfig tracing();

    @ConfigValueExtractor
    interface FallbackLoggingConfig extends TelemetryConfig.LoggingConfig {}

    @ConfigValueExtractor
    interface FallbackMetricsConfig extends TelemetryConfig.MetricsConfig {}

    @ConfigValueExtractor
    interface FallbackTracingConfig extends TelemetryConfig.TracingConfig {

        @Override
        default boolean enabled() {
            return false;
        }
    }
}
