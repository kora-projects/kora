package ru.tinkoff.kora.micrometer.module.grpc.client;

import io.grpc.ServiceDescriptor;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.Nullable;
import ru.tinkoff.grpc.client.telemetry.GrpcClientMetrics;
import ru.tinkoff.grpc.client.telemetry.GrpcClientMetricsFactory;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.net.URI;

public final class MicrometerGrpcClientMetricsFactory implements GrpcClientMetricsFactory {
    private final MeterRegistry registry;

    public MicrometerGrpcClientMetricsFactory(MeterRegistry registry) {
        this.registry = registry;
    }

    @Nullable
    @Override
    public GrpcClientMetrics get(ServiceDescriptor service, TelemetryConfig.MetricsConfig config, URI uri) {
        if (config.enabled() != null && !config.enabled()) {
            return null;
        }
        return new MicrometerGrpcClientMetrics(registry, service, config, uri);
    }
}
