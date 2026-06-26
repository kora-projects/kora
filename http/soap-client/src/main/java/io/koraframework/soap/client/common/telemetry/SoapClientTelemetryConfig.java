package io.koraframework.soap.client.common.telemetry;

import io.koraframework.config.common.annotation.ConfigValueExtractor;
import io.koraframework.telemetry.common.TelemetryConfig;

@ConfigValueExtractor
public interface SoapClientTelemetryConfig extends TelemetryConfig {

    @Override
    SoapClientLoggingConfig logging();

    @Override
    SoapClientMetricsConfig metrics();

    @Override
    SoapClientTracingConfig tracing();

    @ConfigValueExtractor
    interface SoapClientLoggingConfig extends TelemetryConfig.LoggingConfig {}

    @ConfigValueExtractor
    interface SoapClientMetricsConfig extends TelemetryConfig.MetricsConfig {}

    @ConfigValueExtractor
    interface SoapClientTracingConfig extends TelemetryConfig.TracingConfig {}
}
