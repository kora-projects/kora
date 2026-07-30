package io.koraframework.s3.client.aws.telemetry;

import io.koraframework.config.common.annotation.ConfigMapper;
import io.koraframework.telemetry.common.TelemetryConfig;

@ConfigMapper
public interface AwsS3ClientTelemetryConfig extends TelemetryConfig {

    @Override
    S3LoggingConfig logging();

    @Override
    S3MetricsConfig metrics();

    @Override
    S3TracingConfig tracing();

    @ConfigMapper
    interface S3LoggingConfig extends TelemetryConfig.LoggingConfig {}

    @ConfigMapper
    interface S3MetricsConfig extends TelemetryConfig.MetricsConfig {}

    @ConfigMapper
    interface S3TracingConfig extends TelemetryConfig.TracingConfig {}
}
