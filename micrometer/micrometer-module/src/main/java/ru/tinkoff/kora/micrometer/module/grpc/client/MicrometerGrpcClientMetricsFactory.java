package ru.tinkoff.kora.micrometer.module.grpc.client;

import io.grpc.ServiceDescriptor;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.Nullable;
import ru.tinkoff.grpc.client.telemetry.GrpcClientMetrics;
import ru.tinkoff.grpc.client.telemetry.GrpcClientMetricsFactory;
import ru.tinkoff.kora.micrometer.module.MetricsConfig;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.net.URI;

public final class MicrometerGrpcClientMetricsFactory implements GrpcClientMetricsFactory {
    private final MeterRegistry registry;
    private final MetricsConfig metricsConfig;

    public MicrometerGrpcClientMetricsFactory(MeterRegistry registry, MetricsConfig metricsConfig) {
        this.registry = registry;
        this.metricsConfig = metricsConfig;
    }

    @Nullable
    @Override
    public GrpcClientMetrics get(ServiceDescriptor service, TelemetryConfig.MetricsConfig config, URI uri) {
        if (config.enabled() != null && !config.enabled()) {
            return null;
        }
        return switch (metricsConfig.opentelemetrySpec()) {
            case V120 -> new Opentelemetry120GrpcClientMetrics(this.registry, service, config, uri);
            case V123 -> new Opentelemetry123GrpcClientMetrics(this.registry, service, config, uri);
        };
    }
}
