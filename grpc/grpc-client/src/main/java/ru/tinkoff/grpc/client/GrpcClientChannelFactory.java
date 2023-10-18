package ru.tinkoff.grpc.client;

import io.grpc.ChannelCredentials;
import io.grpc.internal.AbstractManagedChannelImplBuilder;
import io.grpc.internal.GrpcUtil;

import java.net.SocketAddress;

public interface GrpcClientChannelFactory {
    default AbstractManagedChannelImplBuilder<?> forAddress(String host, int port) {
        return forTarget(GrpcUtil.authorityFromHostAndPort(host, port));
    }

    default AbstractManagedChannelImplBuilder<?> forAddress(String host, int port, ChannelCredentials creds) {
        return forTarget(GrpcUtil.authorityFromHostAndPort(host, port), creds);
    }

    AbstractManagedChannelImplBuilder<?> forAddress(SocketAddress serverAddress);

    AbstractManagedChannelImplBuilder<?> forAddress(SocketAddress serverAddress, ChannelCredentials creds);

    AbstractManagedChannelImplBuilder<?> forTarget(String target);

    AbstractManagedChannelImplBuilder<?> forTarget(String target, ChannelCredentials creds);
}
