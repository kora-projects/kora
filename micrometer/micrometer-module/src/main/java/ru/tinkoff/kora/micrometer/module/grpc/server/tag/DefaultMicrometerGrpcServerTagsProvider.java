package ru.tinkoff.kora.micrometer.module.grpc.server.tag;

import io.micrometer.core.instrument.Tag;
import io.opentelemetry.semconv.incubating.RpcIncubatingAttributes;

import java.util.List;

public class DefaultMicrometerGrpcServerTagsProvider implements MicrometerGrpcServerTagsProvider {

    @Override
    public Iterable<Tag> getRequestsTags(MetricsKey key) {
        return List.of(
            Tag.of(RpcIncubatingAttributes.RPC_SYSTEM.getKey(), RpcIncubatingAttributes.RpcSystemIncubatingValues.GRPC),
            Tag.of(RpcIncubatingAttributes.RPC_SERVICE.getKey(), key.serviceName()),
            Tag.of(RpcIncubatingAttributes.RPC_METHOD.getKey(), key.methodName())
        );
    }

    @Override
    public Iterable<Tag> getResponsesTags(MetricsKey key) {
        return List.of(
            Tag.of(RpcIncubatingAttributes.RPC_SYSTEM.getKey(), RpcIncubatingAttributes.RpcSystemIncubatingValues.GRPC),
            Tag.of(RpcIncubatingAttributes.RPC_SERVICE.getKey(), key.serviceName()),
            Tag.of(RpcIncubatingAttributes.RPC_METHOD.getKey(), key.methodName())
        );
    }

    @Override
    public Iterable<Tag> getDurationTags(Integer code, MetricsKey key) {
        return List.of(
            Tag.of(RpcIncubatingAttributes.RPC_SYSTEM.getKey(), RpcIncubatingAttributes.RpcSystemIncubatingValues.GRPC),
            Tag.of(RpcIncubatingAttributes.RPC_SERVICE.getKey(), key.serviceName()),
            Tag.of(RpcIncubatingAttributes.RPC_METHOD.getKey(), key.methodName()),
            Tag.of(RpcIncubatingAttributes.RPC_GRPC_STATUS_CODE.getKey(), Integer.toString(code))
        );
    }
}
