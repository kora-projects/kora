package io.koraframework.grpc.client;

import io.grpc.ManagedChannelBuilder;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import org.jspecify.annotations.Nullable;
import io.koraframework.grpc.client.config.DefaultServiceConfigConfigValueExtractor;
import io.koraframework.grpc.client.telemetry.DefaultGrpcClientTelemetryFactory;
import io.koraframework.common.DefaultComponent;
import io.koraframework.common.util.Configurer;

public interface GrpcClientModule {
    @DefaultComponent
    default DefaultServiceConfigConfigValueExtractor defaultServiceConfigConfigValueExtractor() {
        return new DefaultServiceConfigConfigValueExtractor();
    }

    @DefaultComponent
    default DefaultGrpcClientTelemetryFactory defaultGrpcClientTelemetryFactory(@Nullable Tracer tracer, @Nullable MeterRegistry meterRegistry) {
        return new DefaultGrpcClientTelemetryFactory(tracer, meterRegistry);
    }

    @DefaultComponent
    default GrpcOkHttpClientChannelFactory grpcNettyClientChannelFactory(@Nullable Configurer<ManagedChannelBuilder<?>> configurer) {
        return new GrpcOkHttpClientChannelFactory(configurer);
    }
}
