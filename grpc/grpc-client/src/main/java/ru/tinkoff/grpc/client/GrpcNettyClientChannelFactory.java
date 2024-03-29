package ru.tinkoff.grpc.client;

import io.grpc.ChannelCredentials;
import io.grpc.ManagedChannelBuilder;
import io.grpc.netty.NettyChannelBuilder;
import io.netty.channel.EventLoopGroup;
import ru.tinkoff.kora.netty.common.NettyCommonModule;

import java.net.SocketAddress;

public final class GrpcNettyClientChannelFactory implements GrpcClientChannelFactory {
    private final EventLoopGroup eventLoopGroup;

    public GrpcNettyClientChannelFactory(EventLoopGroup eventLoopGroup) {
        this.eventLoopGroup = eventLoopGroup;
    }

    @Override
    public ManagedChannelBuilder<?> forAddress(SocketAddress serverAddress) {
        return NettyChannelBuilder.forAddress(serverAddress)
            .channelType(NettyCommonModule.channelType())
            .eventLoopGroup(this.eventLoopGroup);
    }

    @Override
    public ManagedChannelBuilder<?> forAddress(SocketAddress serverAddress, ChannelCredentials creds) {
        return NettyChannelBuilder.forAddress(serverAddress, creds)
            .channelType(NettyCommonModule.channelType())
            .eventLoopGroup(this.eventLoopGroup);
    }

    @Override
    public ManagedChannelBuilder<?> forTarget(String target) {
        return NettyChannelBuilder.forTarget(target)
            .channelType(NettyCommonModule.channelType())
            .eventLoopGroup(this.eventLoopGroup);
    }

    @Override
    public ManagedChannelBuilder<?> forTarget(String target, ChannelCredentials creds) {
        return NettyChannelBuilder.forTarget(target, creds)
            .channelType(NettyCommonModule.channelType())
            .eventLoopGroup(this.eventLoopGroup);
    }
}
