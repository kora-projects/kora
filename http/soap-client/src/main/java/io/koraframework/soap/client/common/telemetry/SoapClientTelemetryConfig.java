package io.koraframework.soap.client.common.telemetry;

import io.koraframework.config.common.annotation.ConfigMapper;
import io.koraframework.telemetry.common.TelemetryConfig;

@ConfigMapper
public interface SoapClientTelemetryConfig extends TelemetryConfig {

    @Override
    SoapClientLoggingConfig logging();

    @Override
    SoapClientMetricsConfig metrics();

    @Override
    SoapClientTracingConfig tracing();

    @ConfigMapper
    interface SoapClientLoggingConfig extends TelemetryConfig.LoggingConfig {}

    @ConfigMapper
    interface SoapClientMetricsConfig extends TelemetryConfig.MetricsConfig {}

    @ConfigMapper
    interface SoapClientTracingConfig extends TelemetryConfig.TracingConfig {}
}
