package ru.tinkoff.kora.micrometer.module.http.client.tag;

import io.micrometer.core.instrument.Tag;
import io.opentelemetry.semconv.ErrorAttributes;
import io.opentelemetry.semconv.HttpAttributes;
import io.opentelemetry.semconv.ServerAttributes;
import io.opentelemetry.semconv.UrlAttributes;
import ru.tinkoff.kora.http.common.HttpResultCode;
import ru.tinkoff.kora.http.common.header.HttpHeaders;

import java.util.ArrayList;
import java.util.List;

public class Opentelemetry123MicrometerHttpClientTagsProvider implements MicrometerHttpClientTagsProvider {

    @Override
    public List<Tag> getDurationTags(DurationKey key, HttpResultCode resultCode, HttpHeaders headers) {
        final String statusCodeStr = Integer.toString(key.statusCode());

        var tags = new ArrayList<Tag>(7);

        tags.add(Tag.of(HttpAttributes.HTTP_REQUEST_METHOD.getKey(), key.method()));
        tags.add(Tag.of(HttpAttributes.HTTP_RESPONSE_STATUS_CODE.getKey(), statusCodeStr));
        tags.add(Tag.of(ServerAttributes.SERVER_ADDRESS.getKey(), key.host()));
        tags.add(Tag.of(UrlAttributes.URL_SCHEME.getKey(), key.scheme()));
        tags.add(Tag.of(HttpAttributes.HTTP_ROUTE.getKey(), key.target()));
        tags.add(Tag.of("http.status_code", statusCodeStr));

        if (key.errorType() != null) {
            tags.add(Tag.of(ErrorAttributes.ERROR_TYPE.getKey(), key.errorType().getCanonicalName()));
        } else {
            tags.add(Tag.of(ErrorAttributes.ERROR_TYPE.getKey(), ""));
        }

        return tags;
    }
}
