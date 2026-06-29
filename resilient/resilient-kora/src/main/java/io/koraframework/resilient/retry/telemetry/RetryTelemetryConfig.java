package io.koraframework.resilient.retry.telemetry;

import io.koraframework.config.common.annotation.ConfigValueExtractor;
import io.koraframework.telemetry.common.TelemetryConfig;

@ConfigValueExtractor
public interface RetryTelemetryConfig extends TelemetryConfig {

    @Override
    RetryLoggingConfig logging();

    @Override
    RetryMetricsConfig metrics();

    @Override
    RetryTracingConfig tracing();

    @ConfigValueExtractor
    interface RetryLoggingConfig extends TelemetryConfig.LoggingConfig {}

    @ConfigValueExtractor
    interface RetryMetricsConfig extends TelemetryConfig.MetricsConfig {}

    @ConfigValueExtractor
    interface RetryTracingConfig extends TelemetryConfig.TracingConfig {

        @Override
        default boolean enabled() {
            return false;
        }
    }
}
