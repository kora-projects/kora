package ru.tinkoff.kora.micrometer.module.grpc.client.tag;

import io.grpc.Metadata;
import io.micrometer.core.instrument.Tag;
import jakarta.annotation.Nullable;

import java.net.URI;
import java.util.List;

public interface MicrometerGrpcClientTagsProvider {

    List<Tag> getMethodDurationTags(URI uri, MethodDurationKey key, @Nullable Metadata metadata);

    List<Tag> getMessageSendTags(URI uri, MessageSendKey key, Object request);

    List<Tag> getMessageReceivedTags(URI uri, MessageReceivedKey key, Object response);
}
