package io.koraframework.grpc.client;

import io.grpc.ManagedChannelBuilder;
import io.koraframework.config.common.mapper.ConfigValueMapper;
import io.koraframework.grpc.client.channel.GrpcClientChannelFactory;
import io.koraframework.grpc.client.channel.GrpcOkHttpClientChannelFactory;
import io.koraframework.grpc.client.config.DefaultServiceConfig;
import io.koraframework.grpc.client.telemetry.GrpcClientTelemetryFactory;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import org.jspecify.annotations.Nullable;
import io.koraframework.grpc.client.config.DefaultServiceConfigConfigValueMapper;
import io.koraframework.grpc.client.telemetry.impl.DefaultGrpcClientLoggerFactory;
import io.koraframework.grpc.client.telemetry.impl.DefaultGrpcClientMetricsFactory;
import io.koraframework.grpc.client.telemetry.impl.DefaultGrpcClientTelemetryFactory;
import io.koraframework.common.annotation.DefaultComponent;
import io.koraframework.common.annotation.Tag;
import io.koraframework.common.Configurer;
import io.koraframework.grpc.client.telemetry.GrpcClientArgMaskingStrategy;
import io.koraframework.grpc.client.telemetry.GrpcClientTelemetry;

public interface GrpcClientModule {

    @Tag(GrpcClientTelemetry.class)
    @DefaultComponent
    default GrpcClientArgMaskingStrategy defaultGrpcClientArgMaskingStrategy() {
        return (key, value) -> "***";
    }

    @DefaultComponent
    default DefaultGrpcClientLoggerFactory defaultGrpcClientLoggerFactory(@Tag(GrpcClientTelemetry.class) GrpcClientArgMaskingStrategy maskingStrategy) {
        return new DefaultGrpcClientLoggerFactory(maskingStrategy);
    }

    @DefaultComponent
    default ConfigValueMapper<DefaultServiceConfig> defaultServiceConfigConfigValueMapper() {
        return new DefaultServiceConfigConfigValueMapper();
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
