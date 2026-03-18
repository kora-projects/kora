package io.koraframework.http.server.common.request;

import io.koraframework.http.common.body.HttpBodyInput;
import io.koraframework.http.common.body.HttpBodyOutput;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public interface HttpServerRequestBuilder {

    HttpServerRequest build();

    HttpServerRequestBuilder queryParam(String name);

    HttpServerRequestBuilder queryParam(String name, String value);

    default HttpServerRequestBuilder queryParam(String name, Collection<?> values) {
        for (var value : values) {
            this.queryParam(name, Objects.toString(value));
        }
        return this;
    }

    default HttpServerRequestBuilder queryParam(String name, int value) {
        return this.queryParam(name, Integer.toString(value));
    }

    default HttpServerRequestBuilder queryParam(String name, long value) {
        return this.queryParam(name, Long.toString(value));
    }

    default HttpServerRequestBuilder queryParam(String name, boolean value) {
        return this.queryParam(name, Boolean.toString(value));
    }

    default HttpServerRequestBuilder queryParam(String name, UUID value) {
        Objects.requireNonNull(value);
        return this.queryParam(name, Objects.toString(value));
    }

    HttpServerRequestBuilder queryParamRemove(String name);

    HttpServerRequestBuilder header(String name, String value);

    HttpServerRequestBuilder header(String name, List<String> value);

    HttpServerRequestBuilder headerRemove(String name);

    HttpServerRequestBuilder body(HttpBodyInput body);
}
