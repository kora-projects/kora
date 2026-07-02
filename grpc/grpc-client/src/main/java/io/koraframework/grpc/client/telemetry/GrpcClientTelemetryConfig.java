package io.koraframework.grpc.client.telemetry;

import io.koraframework.config.common.annotation.ConfigMapper;
import io.koraframework.telemetry.common.TelemetryConfig;

@ConfigMapper
public interface GrpcClientTelemetryConfig extends TelemetryConfig {

    @Override
    GrpcClientLoggingConfig logging();

    @Override
    GrpcClientMetricsConfig metrics();

    @Override
    GrpcClientTracingConfig tracing();

    @ConfigMapper
    interface GrpcClientLoggingConfig extends LoggingConfig { }

    @ConfigMapper
    interface GrpcClientMetricsConfig extends TelemetryConfig.MetricsConfig { }

    @ConfigMapper
    interface GrpcClientTracingConfig extends TelemetryConfig.TracingConfig { }
}
