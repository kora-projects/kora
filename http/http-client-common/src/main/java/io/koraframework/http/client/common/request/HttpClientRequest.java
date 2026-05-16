package io.koraframework.http.client.common.request;

import io.koraframework.http.common.HttpMethod;
import io.koraframework.http.common.body.HttpBodyOutput;
import io.koraframework.http.common.header.MutableHttpHeaders;
import org.jspecify.annotations.Nullable;

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

    static HttpClientRequest of(String method, URI uri, String uriTemplate, MutableHttpHeaders headers, HttpBodyOutput body, @Nullable Duration requestTimeout) {
        return new SimpleHttpClientRequest(method, uri, uriTemplate, headers, body, requestTimeout);
    }

    static HttpClientRequestBuilder get(String uriTemplate) {
        return new HttpClientRequestBuilderImpl(HttpMethod.GET, uriTemplate);
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
