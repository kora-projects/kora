package ru.tinkoff.grpc.client;

import io.grpc.ChannelCredentials;
import io.grpc.ManagedChannelBuilder;

import java.net.SocketAddress;
import java.net.URI;
import java.net.URISyntaxException;

public interface GrpcClientChannelFactory {

    default ManagedChannelBuilder<?> forAddress(String host, int port) {
        return forTarget(authorityFromHostAndPort(host, port));
    }

    default ManagedChannelBuilder<?> forAddress(String host, int port, ChannelCredentials creds) {
        return forTarget(authorityFromHostAndPort(host, port), creds);
    }

    ManagedChannelBuilder<?> forAddress(SocketAddress serverAddress);

    ManagedChannelBuilder<?> forAddress(SocketAddress serverAddress, ChannelCredentials creds);

    ManagedChannelBuilder<?> forTarget(String target);

    ManagedChannelBuilder<?> forTarget(String target, ChannelCredentials creds);

    private static String authorityFromHostAndPort(String host, int port) {
        try {
            return new URI(null, null, host, port, null, null, null).getAuthority();
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException("Invalid host or port: " + host + " " + port, ex);
        }
    }
}
