package io.koraframework.grpc.client.telemetry;

import io.koraframework.config.common.annotation.ConfigValueExtractor;
import io.koraframework.telemetry.common.TelemetryConfig;

@ConfigValueExtractor
public interface GrpcClientTelemetryConfig extends TelemetryConfig {
    @Override
    GrpcClientLoggingConfig logging();

    @Override
    GrpcClientMetricsConfig metrics();

    @Override
    GrpcClientTracingConfig tracing();

    @ConfigValueExtractor
    interface GrpcClientLoggingConfig extends TelemetryConfig.LogConfig {

    }

    @ConfigValueExtractor
    interface GrpcClientMetricsConfig extends TelemetryConfig.MetricsConfig {

    }

    @ConfigValueExtractor
    interface GrpcClientTracingConfig extends TelemetryConfig.TracingConfig {

    }
}
