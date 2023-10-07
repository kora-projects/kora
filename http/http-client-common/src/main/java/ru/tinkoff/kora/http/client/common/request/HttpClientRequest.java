package ru.tinkoff.kora.http.client.common.request;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.http.common.HttpMethod;
import ru.tinkoff.kora.http.common.MutableHttpHeaders;
import ru.tinkoff.kora.http.common.body.HttpBodyOutput;

import java.net.URI;
import java.time.Duration;

public interface HttpClientRequest {
    String method();

    URI uri();

    String uriTemplate();

    MutableHttpHeaders headers();

    HttpBodyOutput body();

    @Nullable
    Duration requestTimeout();

    default HttpClientRequestBuilder toBuilder() {
        return new HttpClientRequestBuilderImpl(this);
    }

    static HttpClientRequest of(String method, URI uri, String uriTemplate, MutableHttpHeaders headers, HttpBodyOutput body, Duration requestTimeout) {
        return new DefaultHttpClientRequest(method, uri, uriTemplate, headers, body, requestTimeout);
    }

    static HttpClientRequestBuilder get(String path) {
        return new HttpClientRequestBuilderImpl(HttpMethod.GET, path);
    }

    static HttpClientRequestBuilder head(String uriTemplate) {
        return new HttpClientRequestBuilderImpl(HttpMethod.HEAD, uriTemplate);
    }

    static HttpClientRequestBuilder post(String uriTemplate) {
        return new HttpClientRequestBuilderImpl(HttpMethod.POST, uriTemplate);
    }

    static HttpClientRequestBuilder put(String uriTemplate) {
        return new HttpClientRequestBuilderImpl(HttpMethod.PUT, uriTemplate);
    }

    static HttpClientRequestBuilder delete(String uriTemplate) {
        return new HttpClientRequestBuilderImpl(HttpMethod.DELETE, uriTemplate);
    }

    static HttpClientRequestBuilder connect(String uriTemplate) {
        return new HttpClientRequestBuilderImpl(HttpMethod.CONNECT, uriTemplate);
    }

    static HttpClientRequestBuilder options(String uriTemplate) {
        return new HttpClientRequestBuilderImpl(HttpMethod.OPTIONS, uriTemplate);
    }

    static HttpClientRequestBuilder trace(String uriTemplate) {
        return new HttpClientRequestBuilderImpl(HttpMethod.TRACE, uriTemplate);
    }

    static HttpClientRequestBuilder patch(String uriTemplate) {
        return new HttpClientRequestBuilderImpl(HttpMethod.PATCH, uriTemplate);
    }

    static HttpClientRequestBuilder of(String method, String uriTemplate) {
        return new HttpClientRequestBuilderImpl(method, uriTemplate);
    }
}
