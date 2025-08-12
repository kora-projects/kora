package ru.tinkoff.kora.micrometer.module.grpc.client.tag;

import io.grpc.Metadata;
import io.micrometer.core.instrument.Tag;
import jakarta.annotation.Nullable;

import java.net.URI;
import java.util.List;

public interface MicrometerGrpcClientTagsProvider {

    List<Tag> getTags(URI uri, MetricsKey key, @Nullable Metadata metadata);
}
