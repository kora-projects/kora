package ru.tinkoff.kora.micrometer.module.http.server.tag;

import io.micrometer.core.instrument.Tag;
import io.opentelemetry.semconv.SemanticAttributes;

import java.util.ArrayList;
import java.util.List;

/**
 * @see <a href="https://github.com/open-telemetry/semantic-conventions/blob/main/docs/http/http-metrics.md#metric-httpserverrequestduration">link</a>
 */
public final class Opentelemetry123MicrometerHttpServerTagsProvider implements MicrometerHttpServerTagsProvider {

    @Override
    public Iterable<Tag> getActiveRequestsTags(ActiveRequestsKey key) {
        return List.of(
            Tag.of(SemanticAttributes.HTTP_ROUTE.getKey(), key.target()),
            Tag.of(SemanticAttributes.HTTP_REQUEST_METHOD.getKey(), key.method()),
            Tag.of(SemanticAttributes.SERVER_ADDRESS.getKey(), key.host()),
            Tag.of(SemanticAttributes.URL_SCHEME.getKey(), key.scheme())
        );
    }

    @Override
    public Iterable<Tag> getDurationTags(DurationKey key) {
        var list = new ArrayList<Tag>(6);
        if (key.errorType() != null) {
            list.add(Tag.of(SemanticAttributes.ERROR_TYPE.getKey(), key.errorType().getCanonicalName()));
        }
        list.add(Tag.of(SemanticAttributes.HTTP_REQUEST_METHOD.getKey(), key.method()));
        list.add(Tag.of(SemanticAttributes.HTTP_RESPONSE_STATUS_CODE.getKey(), Integer.toString(key.statusCode())));
        list.add(Tag.of(SemanticAttributes.HTTP_ROUTE.getKey(), key.route()));
        list.add(Tag.of(SemanticAttributes.URL_SCHEME.getKey(), key.scheme()));
        list.add(Tag.of(SemanticAttributes.SERVER_ADDRESS.getKey(), key.host()));
        return list;
    }

}
