package io.koraframework.resilient.retry.telemetry;

import io.koraframework.config.common.annotation.ConfigMapper;
import io.koraframework.telemetry.common.TelemetryConfig;

@ConfigMapper
public interface RetryTelemetryConfig extends TelemetryConfig {

    @Override
    RetryLoggingConfig logging();

    @Override
    RetryMetricsConfig metrics();

    @Override
    RetryTracingConfig tracing();

    @ConfigMapper
    interface RetryLoggingConfig extends TelemetryConfig.LoggingConfig {}

    @ConfigMapper
    interface RetryMetricsConfig extends TelemetryConfig.MetricsConfig {}

    @ConfigMapper
    interface RetryTracingConfig extends TelemetryConfig.TracingConfig {

        @Override
        default boolean enabled() {
            return false;
        }
    }
}
