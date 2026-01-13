package ru.tinkoff.grpc.client;

import io.grpc.ManagedChannelBuilder;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import org.jspecify.annotations.Nullable;
import ru.tinkoff.grpc.client.config.DefaultServiceConfigConfigValueExtractor;
import ru.tinkoff.grpc.client.telemetry.DefaultGrpcClientTelemetryFactory;
import ru.tinkoff.kora.common.DefaultComponent;
import ru.tinkoff.kora.common.util.Configurer;

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
