package io.koraframework.grpc.client;

import io.grpc.ManagedChannelBuilder;
import io.koraframework.config.common.extractor.ConfigValueExtractor;
import io.koraframework.grpc.client.channel.GrpcClientChannelFactory;
import io.koraframework.grpc.client.channel.GrpcOkHttpClientChannelFactory;
import io.koraframework.grpc.client.config.DefaultServiceConfig;
import io.koraframework.grpc.client.telemetry.GrpcClientTelemetryFactory;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import org.jspecify.annotations.Nullable;
import io.koraframework.grpc.client.config.DefaultServiceConfigConfigValueExtractor;
import io.koraframework.grpc.client.telemetry.impl.DefaultGrpcClientLoggerFactory;
import io.koraframework.grpc.client.telemetry.impl.DefaultGrpcClientMetricsFactory;
import io.koraframework.grpc.client.telemetry.impl.DefaultGrpcClientTelemetryFactory;
import io.koraframework.common.annotation.DefaultComponent;
import io.koraframework.common.Configurer;

public interface GrpcClientModule {

    @DefaultComponent
    default ConfigValueExtractor<DefaultServiceConfig> defaultServiceConfigConfigValueExtractor() {
        return new DefaultServiceConfigConfigValueExtractor();
    }

    @DefaultComponent
    default GrpcClientTelemetryFactory defaultGrpcClientTelemetryFactory(@Nullable Tracer tracer,
                                                                         @Nullable MeterRegistry meterRegistry,
                                                                         @Nullable DefaultGrpcClientLoggerFactory loggerFactory,
                                                                         @Nullable DefaultGrpcClientMetricsFactory metricsFactory) {
        return new DefaultGrpcClientTelemetryFactory(tracer, meterRegistry, loggerFactory, metricsFactory);
    }

    @DefaultComponent
    default GrpcClientChannelFactory grpcClientChannelFactory(@Nullable Configurer<ManagedChannelBuilder<?>> configurer) {
        return new GrpcOkHttpClientChannelFactory(configurer);
    }
}
