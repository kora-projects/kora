package ru.tinkoff.kora.micrometer.module.grpc.server.tag;

import io.micrometer.core.instrument.Tag;

public interface MicrometerGrpcServerTagsProvider {

    Iterable<Tag> getRequestsTags(MetricsKey key);

    Iterable<Tag> getResponsesTags(MetricsKey key);

    Iterable<Tag> getDurationTags(Integer code, MetricsKey key);
}
