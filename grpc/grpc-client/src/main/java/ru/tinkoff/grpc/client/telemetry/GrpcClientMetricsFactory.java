package ru.tinkoff.grpc.client.telemetry;

import io.grpc.ServiceDescriptor;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.net.URI;

public interface GrpcClientMetricsFactory {
    @Nullable
    GrpcClientMetrics get(ServiceDescriptor service, TelemetryConfig.MetricsConfig config, URI uri);
}
