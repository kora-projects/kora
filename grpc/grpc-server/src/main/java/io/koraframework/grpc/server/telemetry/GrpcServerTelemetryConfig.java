package io.koraframework.grpc.server.telemetry;

import io.koraframework.config.common.annotation.ConfigValueExtractor;
import io.koraframework.telemetry.common.TelemetryConfig;

public interface GrpcServerTelemetryConfig extends TelemetryConfig {
    @Override
    GrpcServerLogConfig logging();

    @Override
    GrpcServerMetricsConfig metrics();

    @Override
    GrpcServerTracingConfig tracing();

    @ConfigValueExtractor
    interface GrpcServerLogConfig extends TelemetryConfig.LogConfig {
    }

    @ConfigValueExtractor
    interface GrpcServerMetricsConfig extends TelemetryConfig.MetricsConfig {
    }

    @ConfigValueExtractor
    interface GrpcServerTracingConfig extends TelemetryConfig.TracingConfig {
    }
}
