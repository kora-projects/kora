package ru.tinkoff.grpc.client;

import io.grpc.ManagedChannelBuilder;

public interface GrpcClientBuilderConfigurer {
    ManagedChannelBuilder<?> configure(ManagedChannelBuilder<?> builder);
}
