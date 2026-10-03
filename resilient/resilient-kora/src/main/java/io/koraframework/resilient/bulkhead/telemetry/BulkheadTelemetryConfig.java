package io.koraframework.resilient.bulkhead.telemetry;

import io.koraframework.config.common.annotation.ConfigMapper;
import io.koraframework.telemetry.common.TelemetryConfig;

@ConfigMapper
public interface BulkheadTelemetryConfig extends TelemetryConfig {

    @Override
    BulkheadLoggingConfig logging();

    @Override
    BulkheadMetricsConfig metrics();

    @Override
    BulkheadTracingConfig tracing();

    @ConfigMapper
    interface BulkheadLoggingConfig extends TelemetryConfig.LoggingConfig {}

    @ConfigMapper
    interface BulkheadMetricsConfig extends TelemetryConfig.MetricsConfig {}

    @ConfigMapper
    interface BulkheadTracingConfig extends TelemetryConfig.TracingConfig {

        @Override
        default boolean enabled() {
            return false;
        }
    }
}
