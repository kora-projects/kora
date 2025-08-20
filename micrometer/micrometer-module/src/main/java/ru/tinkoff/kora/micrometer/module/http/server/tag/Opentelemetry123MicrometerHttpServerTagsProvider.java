package ru.tinkoff.kora.micrometer.module.http.server.tag;

import io.micrometer.core.instrument.Tag;
import io.opentelemetry.semconv.ErrorAttributes;
import io.opentelemetry.semconv.HttpAttributes;
import io.opentelemetry.semconv.ServerAttributes;
import io.opentelemetry.semconv.UrlAttributes;

import java.util.ArrayList;

/**
 * @see <a href="https://github.com/open-telemetry/semantic-conventions/blob/main/docs/http/http-metrics.md#metric-httpserverrequestduration">link</a>
 */
public class Opentelemetry123MicrometerHttpServerTagsProvider implements MicrometerHttpServerTagsProvider {

    @Override
    public Iterable<Tag> getActiveRequestsTags(ActiveRequestsKey key) {
        var tags = new ArrayList<Tag>(5);

        tags.add(Tag.of(HttpAttributes.HTTP_REQUEST_METHOD.getKey(), key.method()));
        tags.add(Tag.of(HttpAttributes.HTTP_ROUTE.getKey(), key.target()));
        tags.add(Tag.of(UrlAttributes.URL_SCHEME.getKey(), key.scheme()));
        tags.add(Tag.of(ServerAttributes.SERVER_ADDRESS.getKey(), key.host()));
        tags.add(Tag.of(ServerAttributes.SERVER_PORT.getKey(), String.valueOf(key.port())));

        return tags;
    }

    @Override
    public Iterable<Tag> getDurationTags(DurationKey key) {
        var tags = new ArrayList<Tag>(7);

        if (key.errorType() != null) {
            tags.add(Tag.of(ErrorAttributes.ERROR_TYPE.getKey(), key.errorType().getCanonicalName()));
        } else {
            tags.add(Tag.of(ErrorAttributes.ERROR_TYPE.getKey(), ""));
        }

        tags.add(Tag.of(HttpAttributes.HTTP_REQUEST_METHOD.getKey(), key.method()));
        tags.add(Tag.of(HttpAttributes.HTTP_RESPONSE_STATUS_CODE.getKey(), Integer.toString(key.statusCode())));
        tags.add(Tag.of(HttpAttributes.HTTP_ROUTE.getKey(), key.route()));
        tags.add(Tag.of(UrlAttributes.URL_SCHEME.getKey(), key.scheme()));
        tags.add(Tag.of(ServerAttributes.SERVER_ADDRESS.getKey(), key.host()));
        tags.add(Tag.of(ServerAttributes.SERVER_PORT.getKey(), String.valueOf(key.port())));

        return tags;
    }
}
