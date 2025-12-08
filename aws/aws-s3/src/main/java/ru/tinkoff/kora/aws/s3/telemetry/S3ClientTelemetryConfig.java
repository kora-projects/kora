package ru.tinkoff.kora.aws.s3.telemetry;

import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

public interface S3ClientTelemetryConfig extends TelemetryConfig {
    @Override
    S3ClientLogConfig logging();

    @Override
    S3ClientMetricsConfig metrics();

    @Override
    S3ClientTracingConfig tracing();

    interface S3ClientLogConfig extends TelemetryConfig.LogConfig {

    }

    interface S3ClientTracingConfig extends TelemetryConfig.TracingConfig {

    }

    interface S3ClientMetricsConfig extends TelemetryConfig.MetricsConfig {

    }
}
