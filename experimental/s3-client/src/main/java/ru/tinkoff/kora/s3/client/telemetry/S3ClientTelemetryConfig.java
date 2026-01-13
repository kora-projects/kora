package ru.tinkoff.kora.s3.client.telemetry;

import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

@ConfigValueExtractor
public interface S3ClientTelemetryConfig extends TelemetryConfig {
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
