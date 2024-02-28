package ru.tinkoff.kora.micrometer.module.http.server.tag;

import io.micrometer.core.instrument.Tag;
import io.opentelemetry.semconv.SemanticAttributes;

import java.util.List;

/**
 * @see <a href="https://github.com/open-telemetry/semantic-conventions/blob/main/docs/http/http-metrics.md#metric-httpserverrequestduration">link</a>
 */
public class DefaultMicrometerHttpServerTagsProvider implements MicrometerHttpServerTagsProvider {

    @Override
    public Iterable<Tag> getActiveRequestsTags(ActiveRequestsKey key) {
        return List.of(
            Tag.of(SemanticAttributes.HTTP_ROUTE.getKey(), key.target()),
            Tag.of(SemanticAttributes.HTTP_REQUEST_METHOD.getKey(), key.method()),
            Tag.of(SemanticAttributes.SERVER_ADDRESS.getKey(), key.host()),
            Tag.of(SemanticAttributes.URL_SCHEME.getKey(), key.scheme()),
            Tag.of(SemanticAttributes.HTTP_TARGET.getKey(), key.target()),
            Tag.of(SemanticAttributes.HTTP_METHOD.getKey(), key.method())
        );
    }

    @Override
    public Iterable<Tag> getDurationTags(DurationKey key) {
        return List.of(
            Tag.of(SemanticAttributes.HTTP_REQUEST_METHOD.getKey(), key.method()),
            Tag.of(SemanticAttributes.HTTP_RESPONSE_STATUS_CODE.getKey(), Integer.toString(key.statusCode())),
            Tag.of(SemanticAttributes.HTTP_ROUTE.getKey(), key.target()),
            Tag.of(SemanticAttributes.SERVER_ADDRESS.getKey(), key.host()),
            Tag.of(SemanticAttributes.URL_SCHEME.getKey(), key.scheme()),
            Tag.of(SemanticAttributes.HTTP_TARGET.getKey(), key.target()),
            Tag.of(SemanticAttributes.HTTP_METHOD.getKey(), key.method()),
            Tag.of(SemanticAttributes.HTTP_STATUS_CODE.getKey(), Integer.toString(key.statusCode()))
        );
    }

}
