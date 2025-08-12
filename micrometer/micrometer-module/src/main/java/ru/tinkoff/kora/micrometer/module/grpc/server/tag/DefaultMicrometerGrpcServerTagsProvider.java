package ru.tinkoff.kora.micrometer.module.grpc.server.tag;

import io.micrometer.core.instrument.Tag;
import io.opentelemetry.semconv.incubating.RpcIncubatingAttributes;

import java.util.ArrayList;
import java.util.List;

public class DefaultMicrometerGrpcServerTagsProvider implements MicrometerGrpcServerTagsProvider {

    @Override
    public List<Tag> getRequestsTags(MetricsKey key) {
        var tags = new ArrayList<Tag>(3);

        tags.add(Tag.of(RpcIncubatingAttributes.RPC_SYSTEM.getKey(), RpcIncubatingAttributes.RpcSystemIncubatingValues.GRPC));
        tags.add(Tag.of(RpcIncubatingAttributes.RPC_SERVICE.getKey(), key.serviceName()));
        tags.add(Tag.of(RpcIncubatingAttributes.RPC_METHOD.getKey(), key.methodName()));

        return tags;
    }

    @Override
    public List<Tag> getResponsesTags(MetricsKey key) {
        var tags = new ArrayList<Tag>(3);

        tags.add(Tag.of(RpcIncubatingAttributes.RPC_SYSTEM.getKey(), RpcIncubatingAttributes.RpcSystemIncubatingValues.GRPC));
        tags.add(Tag.of(RpcIncubatingAttributes.RPC_SERVICE.getKey(), key.serviceName()));
        tags.add(Tag.of(RpcIncubatingAttributes.RPC_METHOD.getKey(), key.methodName()));

        return tags;
    }

    @Override
    public List<Tag> getDurationTags(Integer code, MetricsKey key) {
        var tags = new ArrayList<Tag>(4);

        tags.add(Tag.of(RpcIncubatingAttributes.RPC_SYSTEM.getKey(), RpcIncubatingAttributes.RpcSystemIncubatingValues.GRPC));
        tags.add(Tag.of(RpcIncubatingAttributes.RPC_SERVICE.getKey(), key.serviceName()));
        tags.add(Tag.of(RpcIncubatingAttributes.RPC_METHOD.getKey(), key.methodName()));
        tags.add(Tag.of(RpcIncubatingAttributes.RPC_GRPC_STATUS_CODE.getKey(), Integer.toString(code)));

        return tags;
    }
}
