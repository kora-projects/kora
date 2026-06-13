package io.koraframework.scheduling.common.telemetry;

import io.koraframework.config.common.annotation.ConfigValueExtractor;
import io.koraframework.telemetry.common.TelemetryConfig;

@ConfigValueExtractor
public interface SchedulingTelemetryConfig extends TelemetryConfig {

    @Override
    SchedulingLogConfig logging();

    @Override
    SchedulingTracingConfig tracing();

    @Override
    SchedulingMetricsConfig metrics();

    @ConfigValueExtractor
    interface SchedulingLogConfig extends TelemetryConfig.LogConfig {}

    @ConfigValueExtractor
    interface SchedulingTracingConfig extends TelemetryConfig.TracingConfig {}

    @ConfigValueExtractor
    interface SchedulingMetricsConfig extends TelemetryConfig.MetricsConfig {}
}
