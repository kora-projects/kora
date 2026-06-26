package io.koraframework.s3.client.aws.telemetry;

import io.koraframework.config.common.annotation.ConfigValueExtractor;
import io.koraframework.telemetry.common.TelemetryConfig;

@ConfigValueExtractor
public interface AwsS3ClientTelemetryConfig extends TelemetryConfig {

    @Override
    S3LoggingConfig logging();

    @Override
    S3MetricsConfig metrics();

    @Override
    S3TracingConfig tracing();

    @ConfigValueExtractor
    interface S3LoggingConfig extends TelemetryConfig.LoggingConfig { }

    @ConfigValueExtractor
    interface S3MetricsConfig extends TelemetryConfig.MetricsConfig { }

    @ConfigValueExtractor
    interface S3TracingConfig extends TelemetryConfig.TracingConfig { }
}
