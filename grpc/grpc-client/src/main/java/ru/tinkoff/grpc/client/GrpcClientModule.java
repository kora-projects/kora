package ru.tinkoff.grpc.client;

import io.micrometer.core.instrument.MeterRegistry;
import io.netty.channel.EventLoopGroup;
import io.opentelemetry.api.trace.Tracer;
import jakarta.annotation.Nullable;
import ru.tinkoff.grpc.client.config.DefaultServiceConfigConfigValueExtractor;
import ru.tinkoff.grpc.client.telemetry.DefaultGrpcClientTelemetryFactory;
import ru.tinkoff.kora.common.DefaultComponent;
import ru.tinkoff.kora.common.Tag;
import ru.tinkoff.kora.netty.common.NettyChannelFactory;
import ru.tinkoff.kora.netty.common.NettyCommonModule;

public interface GrpcClientModule extends NettyCommonModule {
    @DefaultComponent
    default DefaultServiceConfigConfigValueExtractor defaultServiceConfigConfigValueExtractor() {
        return new DefaultServiceConfigConfigValueExtractor();
    }

    @DefaultComponent
    default DefaultGrpcClientTelemetryFactory defaultGrpcClientTelemetryFactory(@Nullable Tracer tracer, @Nullable MeterRegistry meterRegistry) {
        return new DefaultGrpcClientTelemetryFactory(tracer, meterRegistry);
    }

    @DefaultComponent
    default GrpcNettyClientChannelFactory grpcNettyClientChannelFactory(@Tag(WorkerLoopGroup.class) EventLoopGroup eventLoopGroup,
                                                                        NettyChannelFactory nettyChannelFactory) {
        return new GrpcNettyClientChannelFactory(eventLoopGroup, nettyChannelFactory);
    }
}
