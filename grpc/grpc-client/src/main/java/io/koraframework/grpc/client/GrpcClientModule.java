package io.koraframework.grpc.client;

import io.grpc.ManagedChannelBuilder;
import io.koraframework.grpc.client.channel.GrpcOkHttpClientChannelFactory;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import org.jspecify.annotations.Nullable;
import io.koraframework.grpc.client.config.DefaultServiceConfigConfigValueExtractor;
import io.koraframework.grpc.client.telemetry.impl.DefaultGrpcClientLoggerFactory;
import io.koraframework.grpc.client.telemetry.impl.DefaultGrpcClientMetricsFactory;
import io.koraframework.grpc.client.telemetry.impl.DefaultGrpcClientTelemetryFactory;
import io.koraframework.common.DefaultComponent;
import io.koraframework.common.util.Configurer;

public interface GrpcClientModule {

    @DefaultComponent
    default DefaultServiceConfigConfigValueExtractor defaultServiceConfigConfigValueExtractor() {
        return new DefaultServiceConfigConfigValueExtractor();
    }

    @DefaultComponent
    default DefaultGrpcClientTelemetryFactory defaultGrpcClientTelemetryFactory(@Nullable Tracer tracer,
                                                                               @Nullable MeterRegistry meterRegistry,
                                                                               @Nullable DefaultGrpcClientLoggerFactory loggerFactory,
                                                                               @Nullable DefaultGrpcClientMetricsFactory metricsFactory) {
        return new DefaultGrpcClientTelemetryFactory(tracer, meterRegistry, loggerFactory, metricsFactory);
    }

    @DefaultComponent
    default GrpcOkHttpClientChannelFactory grpcClientChannelFactory(@Nullable Configurer<ManagedChannelBuilder<?>> configurer) {
        return new GrpcOkHttpClientChannelFactory(configurer);
    }
}
