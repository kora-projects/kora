package ru.tinkoff.kora.micrometer.module.grpc.client.tag;

import io.grpc.Metadata;
import io.micrometer.core.instrument.Tag;
import io.opentelemetry.semconv.ErrorAttributes;
import io.opentelemetry.semconv.ServerAttributes;
import io.opentelemetry.semconv.incubating.RpcIncubatingAttributes;
import jakarta.annotation.Nullable;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class DefaultMicrometerGrpcClientTagsProvider implements MicrometerGrpcClientTagsProvider {

    @Override
    public List<Tag> getMethodDurationTags(URI uri, MethodDurationKey key, @Nullable Metadata metadata) {
        var rpcService = Objects.requireNonNullElse(key.serviceName(), "GrpcService");
        var serverAddress = uri.getHost();
        var serverPort = uri.getPort();
        if (serverPort == -1) {
            serverPort = 80;
        }

        var tags = new ArrayList<Tag>(7);

        tags.add(Tag.of(RpcIncubatingAttributes.RPC_METHOD.getKey(), key.methodName()));
        tags.add(Tag.of(RpcIncubatingAttributes.RPC_SERVICE.getKey(), rpcService));
        tags.add(Tag.of(RpcIncubatingAttributes.RPC_SYSTEM.getKey(), RpcIncubatingAttributes.RpcSystemIncubatingValues.GRPC));
        tags.add(Tag.of(ServerAttributes.SERVER_ADDRESS.getKey(), serverAddress));
        tags.add(Tag.of(ServerAttributes.SERVER_PORT.getKey(), String.valueOf(serverPort)));

        if (key.code() != null) {
            tags.add(Tag.of(RpcIncubatingAttributes.RPC_GRPC_STATUS_CODE.getKey(), String.valueOf(key.code())));
        } else {
            tags.add(Tag.of(RpcIncubatingAttributes.RPC_GRPC_STATUS_CODE.getKey(), ""));
        }

        if (key.errorType() != null) {
            tags.add(Tag.of(ErrorAttributes.ERROR_TYPE.getKey(), key.errorType().getCanonicalName()));
        } else {
            tags.add(Tag.of(ErrorAttributes.ERROR_TYPE.getKey(), ""));
        }

        return tags;
    }

    @Override
    public List<Tag> getMessageSendTags(URI uri, MessageSendKey key, Object request) {
        var rpcService = Objects.requireNonNullElse(key.serviceName(), "GrpcService");
        var serverAddress = uri.getHost();
        var serverPort = uri.getPort();
        if (serverPort == -1) {
            serverPort = 80;
        }

        var tags = new ArrayList<Tag>(5);

        tags.add(Tag.of(RpcIncubatingAttributes.RPC_METHOD.getKey(), key.methodName()));
        tags.add(Tag.of(RpcIncubatingAttributes.RPC_SERVICE.getKey(), rpcService));
        tags.add(Tag.of(RpcIncubatingAttributes.RPC_SYSTEM.getKey(), RpcIncubatingAttributes.RpcSystemIncubatingValues.GRPC));
        tags.add(Tag.of(ServerAttributes.SERVER_ADDRESS.getKey(), serverAddress));
        tags.add(Tag.of(ServerAttributes.SERVER_PORT.getKey(), String.valueOf(serverPort)));

        return tags;
    }

    @Override
    public List<Tag> getMessageReceivedTags(URI uri, MessageReceivedKey key, Object response) {
        var rpcService = Objects.requireNonNullElse(key.serviceName(), "GrpcService");
        var serverAddress = uri.getHost();
        var serverPort = uri.getPort();
        if (serverPort == -1) {
            serverPort = 80;
        }

        var tags = new ArrayList<Tag>(5);

        tags.add(Tag.of(RpcIncubatingAttributes.RPC_METHOD.getKey(), key.methodName()));
        tags.add(Tag.of(RpcIncubatingAttributes.RPC_SERVICE.getKey(), rpcService));
        tags.add(Tag.of(RpcIncubatingAttributes.RPC_SYSTEM.getKey(), RpcIncubatingAttributes.RpcSystemIncubatingValues.GRPC));
        tags.add(Tag.of(ServerAttributes.SERVER_ADDRESS.getKey(), serverAddress));
        tags.add(Tag.of(ServerAttributes.SERVER_PORT.getKey(), String.valueOf(serverPort)));

        return tags;
    }
}
