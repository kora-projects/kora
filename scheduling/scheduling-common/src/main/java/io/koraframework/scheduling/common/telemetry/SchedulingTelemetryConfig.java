package io.koraframework.scheduling.common.telemetry;

import io.koraframework.config.common.annotation.ConfigMapper;
import io.koraframework.telemetry.common.TelemetryConfig;

@ConfigMapper
public interface SchedulingTelemetryConfig extends TelemetryConfig {

    @Override
    SchedulingLoggingConfig logging();

    @Override
    SchedulingMetricsConfig metrics();

    @Override
    SchedulingTracingConfig tracing();

    @ConfigMapper
    interface SchedulingLoggingConfig extends TelemetryConfig.LoggingConfig {}

    @ConfigMapper
    interface SchedulingMetricsConfig extends TelemetryConfig.MetricsConfig {}

    @ConfigMapper
    interface SchedulingTracingConfig extends TelemetryConfig.TracingConfig {}
}
