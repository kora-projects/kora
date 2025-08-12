package ru.tinkoff.kora.micrometer.module.grpc.client;

import io.grpc.ServiceDescriptor;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.Nullable;
import ru.tinkoff.grpc.client.telemetry.GrpcClientMetrics;
import ru.tinkoff.grpc.client.telemetry.GrpcClientMetricsFactory;
import ru.tinkoff.kora.micrometer.module.MetricsConfig;
import ru.tinkoff.kora.micrometer.module.grpc.client.tag.MicrometerGrpcClientTagsProvider;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.net.URI;
import java.util.Objects;

public final class MicrometerGrpcClientMetricsFactory implements GrpcClientMetricsFactory {

    private final MeterRegistry registry;
    private final MetricsConfig metricsConfig;
    private final MicrometerGrpcClientTagsProvider tagsProvider;

    public MicrometerGrpcClientMetricsFactory(MeterRegistry registry,
                                              MetricsConfig metricsConfig,
                                              MicrometerGrpcClientTagsProvider tagsProvider) {
        this.registry = registry;
        this.metricsConfig = metricsConfig;
        this.tagsProvider = tagsProvider;
    }

    @Nullable
    @Override
    public GrpcClientMetrics get(ServiceDescriptor service, TelemetryConfig.MetricsConfig config, URI uri) {
        if (Objects.requireNonNullElse(config.enabled(), true)) {
            return switch (metricsConfig.opentelemetrySpec()) {
                case V120 -> new Opentelemetry120GrpcClientMetrics(this.registry, service, config, uri, tagsProvider);
                case V123 -> new Opentelemetry123GrpcClientMetrics(this.registry, service, config, uri, tagsProvider);
            };
        } else {
            return null;
        }
    }
}
