package io.koraframework.openfeature.telemetry;

import io.koraframework.config.common.annotation.ConfigMapper;
import io.koraframework.telemetry.common.TelemetryConfig;

@ConfigMapper
public interface OpenfeatureTelemetryConfig extends TelemetryConfig {

    @Override
    OpenfeatureLoggingConfig logging();

    @Override
    OpenfeatureMetricsConfig metrics();

    @Override
    OpenfeatureTracingConfig tracing();

    @ConfigMapper
    interface OpenfeatureLoggingConfig extends LoggingConfig {}

    @ConfigMapper
    interface OpenfeatureMetricsConfig extends MetricsConfig {}

    @ConfigMapper
    interface OpenfeatureTracingConfig extends TracingConfig {}
}
