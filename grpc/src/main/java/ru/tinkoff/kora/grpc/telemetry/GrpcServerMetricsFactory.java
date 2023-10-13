package ru.tinkoff.kora.grpc.telemetry;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

public interface GrpcServerMetricsFactory {
    GrpcServerMetrics get(TelemetryConfig.MetricsConfig config, ServerCall<?, ?> call, Metadata headers, String serviceName, String methodName);
}
