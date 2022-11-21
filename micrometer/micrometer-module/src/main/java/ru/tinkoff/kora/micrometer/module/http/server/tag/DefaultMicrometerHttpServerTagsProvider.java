package ru.tinkoff.kora.micrometer.module.http.server.tag;

import io.micrometer.core.instrument.Tag;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;

import java.util.List;

public class DefaultMicrometerHttpServerTagsProvider implements MicrometerHttpServerTagsProvider {

    @Override
    public Iterable<Tag> getActiveRequestsTags(ActiveRequestsKey key) {
        return List.of(
            Tag.of(SemanticAttributes.HTTP_TARGET.getKey(), key.target()),
            Tag.of(SemanticAttributes.HTTP_METHOD.getKey(), key.method()),
            Tag.of(SemanticAttributes.NET_HOST_NAME.getKey(), key.host()),
            Tag.of(SemanticAttributes.HTTP_SCHEME.getKey(), key.scheme())
        );
    }

    @Override
    public Iterable<Tag> getDurationTags(DurationKey key) {
        return List.of(
            Tag.of(SemanticAttributes.HTTP_TARGET.getKey(), key.target()),
            Tag.of(SemanticAttributes.HTTP_METHOD.getKey(), key.method()),
            Tag.of(SemanticAttributes.NET_HOST_NAME.getKey(), key.host()),
            Tag.of(SemanticAttributes.HTTP_SCHEME.getKey(), key.scheme()),
            Tag.of(SemanticAttributes.HTTP_STATUS_CODE.getKey(), Integer.toString(key.statusCode()))
        );
    }

}
