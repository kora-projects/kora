package ru.tinkoff.grpc.client;

import io.grpc.ChannelCredentials;
import io.grpc.ManagedChannelBuilder;
import io.grpc.internal.GrpcUtil;

import java.net.SocketAddress;

public interface GrpcClientChannelFactory {
    default ManagedChannelBuilder<?> forAddress(String host, int port) {
        return forTarget(GrpcUtil.authorityFromHostAndPort(host, port));
    }

    default ManagedChannelBuilder<?> forAddress(String host, int port, ChannelCredentials creds) {
        return forTarget(GrpcUtil.authorityFromHostAndPort(host, port), creds);
    }

    ManagedChannelBuilder<?> forAddress(SocketAddress serverAddress);

    ManagedChannelBuilder<?> forAddress(SocketAddress serverAddress, ChannelCredentials creds);

    ManagedChannelBuilder<?> forTarget(String target);

    ManagedChannelBuilder<?> forTarget(String target, ChannelCredentials creds);
}
