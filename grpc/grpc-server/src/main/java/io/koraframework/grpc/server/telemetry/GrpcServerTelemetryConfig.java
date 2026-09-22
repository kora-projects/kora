package io.koraframework.grpc.server.telemetry;

import io.koraframework.config.common.annotation.ConfigMapper;
import io.koraframework.telemetry.common.TelemetryConfig;

import java.util.Set;

@ConfigMapper
public interface GrpcServerTelemetryConfig extends TelemetryConfig {

    @Override
    GrpcServerLoggingConfig logging();

    @Override
    GrpcServerMetricsConfig metrics();

    @Override
    GrpcServerTracingConfig tracing();

    @ConfigMapper
    interface GrpcServerLoggingConfig extends TelemetryConfig.LoggingConfig {
        default Set<String> maskHeaders() {
            return Set.of("authorization", "cookie", "set-cookie");
        }
    }

    @ConfigMapper
    interface GrpcServerMetricsConfig extends TelemetryConfig.MetricsConfig {}

    @ConfigMapper
    interface GrpcServerTracingConfig extends TelemetryConfig.TracingConfig {}
}
