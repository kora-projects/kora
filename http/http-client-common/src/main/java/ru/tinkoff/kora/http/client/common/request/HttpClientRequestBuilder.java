package ru.tinkoff.kora.http.client.common.request;

import ru.tinkoff.kora.http.common.body.HttpBodyOutput;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public interface HttpClientRequestBuilder {

    HttpClientRequest build();

    HttpClientRequestBuilder templateParam(String name, String value);

    default HttpClientRequestBuilder templateParam(String name, int value) {
        return this.templateParam(name, Integer.toString(value));
    }

    default HttpClientRequestBuilder templateParam(String name, long value) {
        return this.templateParam(name, Long.toString(value));
    }

    default HttpClientRequestBuilder templateParam(String name, UUID value) {
        Objects.requireNonNull(value);

        return this.templateParam(name, Objects.toString(value));
    }

    HttpClientRequestBuilder queryParam(String name);

    HttpClientRequestBuilder queryParam(String name, String value);

    default HttpClientRequestBuilder queryParam(String name, Collection<?> values) {
        for (var value : values) {
            this.queryParam(name, Objects.toString(value));
        }
        return this;
    }

    default HttpClientRequestBuilder queryParam(String name, int value) {
        return this.queryParam(name, Integer.toString(value));
    }

    default HttpClientRequestBuilder queryParam(String name, long value) {
        return this.queryParam(name, Long.toString(value));
    }

    default HttpClientRequestBuilder queryParam(String name, boolean value) {
        return this.queryParam(name, Boolean.toString(value));
    }

    default HttpClientRequestBuilder queryParam(String name, UUID value) {
        Objects.requireNonNull(value);

        return this.queryParam(name, Objects.toString(value));
    }

    HttpClientRequestBuilder header(String name, String value);

    HttpClientRequestBuilder header(String name, List<String> value);

    HttpClientRequestBuilder requestTimeout(int timeoutMillis);

    HttpClientRequestBuilder requestTimeout(Duration timeout);

    HttpClientRequestBuilder body(HttpBodyOutput body);
}
