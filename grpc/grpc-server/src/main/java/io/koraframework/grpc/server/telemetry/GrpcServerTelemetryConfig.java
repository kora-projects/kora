package io.koraframework.grpc.server.telemetry;

import io.koraframework.config.common.annotation.ConfigValueExtractor;
import io.koraframework.telemetry.common.TelemetryConfig;

@ConfigValueExtractor
public interface GrpcServerTelemetryConfig extends TelemetryConfig {

    @Override
    GrpcServerLoggingConfig logging();

    @Override
    GrpcServerMetricsConfig metrics();

    @Override
    GrpcServerTracingConfig tracing();

    @ConfigValueExtractor
    interface GrpcServerLoggingConfig extends TelemetryConfig.LoggingConfig {}

    @ConfigValueExtractor
    interface GrpcServerMetricsConfig extends TelemetryConfig.MetricsConfig {}

    @ConfigValueExtractor
    interface GrpcServerTracingConfig extends TelemetryConfig.TracingConfig {}
}
