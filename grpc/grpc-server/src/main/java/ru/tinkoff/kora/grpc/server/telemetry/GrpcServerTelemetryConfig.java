package ru.tinkoff.kora.grpc.server.telemetry;

import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

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
