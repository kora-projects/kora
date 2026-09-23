package io.koraframework.grpc.client.telemetry;

import io.koraframework.config.common.annotation.ConfigMapper;
import io.koraframework.telemetry.common.TelemetryConfig;

import java.util.Set;

@ConfigMapper
public interface GrpcClientTelemetryConfig extends TelemetryConfig {

    @Override
    GrpcClientLoggingConfig logging();

    @Override
    GrpcClientMetricsConfig metrics();

    @Override
    GrpcClientTracingConfig tracing();

    @ConfigMapper
    interface GrpcClientLoggingConfig extends TelemetryConfig.LoggingConfig {
        default Set<String> maskHeaders() {
            return Set.of("authorization", "cookie", "set-cookie");
        }
    }

    @ConfigMapper
    interface GrpcClientMetricsConfig extends TelemetryConfig.MetricsConfig { }

    @ConfigMapper
    interface GrpcClientTracingConfig extends TelemetryConfig.TracingConfig { }
}
