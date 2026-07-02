package io.koraframework.s3.client.kora.telemetry;

import io.koraframework.config.common.annotation.ConfigMapper;
import io.koraframework.telemetry.common.TelemetryConfig;

@ConfigMapper
public interface S3ClientTelemetryConfig extends TelemetryConfig {

    @Override
    S3ClientLoggingConfig logging();

    @Override
    S3ClientMetricsConfig metrics();

    @Override
    S3ClientTracingConfig tracing();

    @ConfigMapper
    interface S3ClientLoggingConfig extends TelemetryConfig.LoggingConfig {}

    @ConfigMapper
    interface S3ClientTracingConfig extends TelemetryConfig.TracingConfig {}

    @ConfigMapper
    interface S3ClientMetricsConfig extends TelemetryConfig.MetricsConfig {}
}
