package io.koraframework.http.client.common.request;

import io.koraframework.http.common.body.HttpBodyOutput;
import io.koraframework.http.common.header.MutableHttpHeaders;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;

record SimpleHttpClientRequest(String method,
                               URI uri,
                               String uriTemplate,
                               MutableHttpHeaders headers,
                               HttpBodyOutput body,
                               @Nullable Duration requestTimeout) implements HttpClientRequest {

    public SimpleHttpClientRequest {
        Objects.requireNonNull(method);
        Objects.requireNonNull(uri);
        Objects.requireNonNull(headers);
        Objects.requireNonNull(body);
        method = method.toUpperCase();
    }

    @Override
    public String toString() {
        return method + " " + uri;
    }
}
