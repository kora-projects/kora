package ru.tinkoff.grpc.client;

import io.netty.channel.EventLoopGroup;
import jakarta.annotation.Nullable;
import ru.tinkoff.grpc.client.config.DefaultServiceConfigConfigValueExtractor;
import ru.tinkoff.grpc.client.telemetry.DefaultGrpcClientTelemetryFactory;
import ru.tinkoff.grpc.client.telemetry.GrpcClientLoggerFactory;
import ru.tinkoff.grpc.client.telemetry.GrpcClientMetricsFactory;
import ru.tinkoff.grpc.client.telemetry.GrpcClientTracerFactory;
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
    default DefaultGrpcClientTelemetryFactory defaultGrpcClientTelemetryFactory(@Nullable GrpcClientMetricsFactory metrics, @Nullable GrpcClientTracerFactory tracer, @Nullable GrpcClientLoggerFactory logger) {
        return new DefaultGrpcClientTelemetryFactory(metrics, tracer, logger);
    }

    @DefaultComponent
    default GrpcNettyClientChannelFactory grpcNettyClientChannelFactory(@Tag(WorkerLoopGroup.class) EventLoopGroup eventLoopGroup,
                                                                        NettyChannelFactory nettyChannelFactory) {
        return new GrpcNettyClientChannelFactory(eventLoopGroup, nettyChannelFactory);
    }
}
