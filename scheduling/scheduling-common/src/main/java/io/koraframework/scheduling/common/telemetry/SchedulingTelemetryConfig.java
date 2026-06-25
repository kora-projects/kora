package io.koraframework.scheduling.common.telemetry;

import io.koraframework.config.common.annotation.ConfigValueExtractor;
import io.koraframework.telemetry.common.TelemetryConfig;

@ConfigValueExtractor
public interface SchedulingTelemetryConfig extends TelemetryConfig {

    @Override
    SchedulingLoggingConfig logging();

    @Override
    SchedulingMetricsConfig metrics();

    @Override
    SchedulingTracingConfig tracing();

    @ConfigValueExtractor
    interface SchedulingLoggingConfig extends TelemetryConfig.LoggingConfig {}

    @ConfigValueExtractor
    interface SchedulingMetricsConfig extends TelemetryConfig.MetricsConfig {}

    @ConfigValueExtractor
    interface SchedulingTracingConfig extends TelemetryConfig.TracingConfig {}
}
