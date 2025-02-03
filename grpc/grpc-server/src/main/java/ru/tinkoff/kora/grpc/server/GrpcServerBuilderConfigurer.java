package ru.tinkoff.kora.grpc.server;

import io.grpc.netty.NettyServerBuilder;

public interface GrpcServerBuilderConfigurer {
    NettyServerBuilder configure(NettyServerBuilder builder);
}
