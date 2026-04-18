package io.koraframework.s3.client.aws.telemetry;

import io.koraframework.config.common.annotation.ConfigValueExtractor;
import io.koraframework.telemetry.common.TelemetryConfig;

@ConfigValueExtractor
public interface AwsS3ClientTelemetryConfig extends TelemetryConfig {

    @Override
    S3ClientLogConfig logging();

    @Override
    S3ClientMetricsConfig metrics();

    @Override
    S3ClientTracingConfig tracing();

    @ConfigValueExtractor
    interface S3ClientLogConfig extends TelemetryConfig.LogConfig {

    }

    @ConfigValueExtractor
    interface S3ClientTracingConfig extends TelemetryConfig.TracingConfig {

    }

    @ConfigValueExtractor
    interface S3ClientMetricsConfig extends TelemetryConfig.MetricsConfig {

    }
}
