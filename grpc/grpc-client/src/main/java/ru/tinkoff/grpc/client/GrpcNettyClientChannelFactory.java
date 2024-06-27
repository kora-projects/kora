package ru.tinkoff.grpc.client;

import io.grpc.ChannelCredentials;
import io.grpc.ManagedChannelBuilder;
import io.grpc.netty.NettyChannelBuilder;
import io.netty.channel.EventLoopGroup;
import ru.tinkoff.kora.netty.common.NettyChannelFactory;
import ru.tinkoff.kora.netty.common.NettyCommonModule;

import java.net.SocketAddress;

public final class GrpcNettyClientChannelFactory implements GrpcClientChannelFactory {

    private final EventLoopGroup eventLoopGroup;
    private final NettyChannelFactory nettyChannelFactory;

    public GrpcNettyClientChannelFactory(EventLoopGroup eventLoopGroup, NettyChannelFactory nettyChannelFactory) {
        this.eventLoopGroup = eventLoopGroup;
        this.nettyChannelFactory = nettyChannelFactory;
    }

    @Override
    public ManagedChannelBuilder<?> forAddress(SocketAddress serverAddress) {
        return NettyChannelBuilder.forAddress(serverAddress)
            .channelFactory(nettyChannelFactory.getClientFactory())
            .eventLoopGroup(this.eventLoopGroup);
    }

    @Override
    public ManagedChannelBuilder<?> forAddress(SocketAddress serverAddress, ChannelCredentials creds) {
        return NettyChannelBuilder.forAddress(serverAddress, creds)
            .channelFactory(nettyChannelFactory.getClientFactory())
            .eventLoopGroup(this.eventLoopGroup);
    }

    @Override
    public ManagedChannelBuilder<?> forTarget(String target) {
        return NettyChannelBuilder.forTarget(target)
            .channelFactory(nettyChannelFactory.getClientFactory())
            .eventLoopGroup(this.eventLoopGroup);
    }

    @Override
    public ManagedChannelBuilder<?> forTarget(String target, ChannelCredentials creds) {
        return NettyChannelBuilder.forTarget(target, creds)
            .channelFactory(nettyChannelFactory.getClientFactory())
            .eventLoopGroup(this.eventLoopGroup);
    }
}
