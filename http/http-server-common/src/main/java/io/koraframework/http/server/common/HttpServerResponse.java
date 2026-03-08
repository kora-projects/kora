package io.koraframework.http.server.common;

import org.jspecify.annotations.Nullable;
import io.koraframework.http.common.body.HttpBody;
import io.koraframework.http.common.body.HttpBodyOutput;
import io.koraframework.http.common.header.HttpHeaders;
import io.koraframework.http.common.header.MutableHttpHeaders;

public interface HttpServerResponse {

    int code();

    MutableHttpHeaders headers();

    @Nullable
    HttpBodyOutput body();

    static HttpServerResponse of(int code) {
        return new SimpleHttpServerResponse(code, HttpHeaders.of(), null);
    }

    static HttpServerResponse of(int code, @Nullable HttpBodyOutput body) {
        return new SimpleHttpServerResponse(code, HttpHeaders.of(), body);
    }

    static HttpServerResponse of(int code, @Nullable HttpHeaders headers, HttpBodyOutput body) {
        return new SimpleHttpServerResponse(code, headers != null ? headers.toMutable() : HttpHeaders.of(), body);
    }

    static HttpServerResponse of(int code, @Nullable HttpHeaders headers) {
        return new SimpleHttpServerResponse(code, headers != null ? headers.toMutable() : HttpHeaders.of(), HttpBody.empty());
    }

    static HttpServerResponse of(int code, @Nullable MutableHttpHeaders headers, @Nullable HttpBodyOutput body) {
        return new SimpleHttpServerResponse(code, headers == null ? HttpHeaders.of() : headers, body);
    }

    static HttpServerResponse of(int code, @Nullable MutableHttpHeaders headers) {
        return new SimpleHttpServerResponse(code, headers == null ? HttpHeaders.of() : headers, HttpBody.empty());
    }
}
