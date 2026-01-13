package ru.tinkoff.kora.http.server.common;

import org.jspecify.annotations.Nullable;
import ru.tinkoff.kora.http.common.body.HttpBody;
import ru.tinkoff.kora.http.common.body.HttpBodyOutput;
import ru.tinkoff.kora.http.common.header.HttpHeaders;
import ru.tinkoff.kora.http.common.header.MutableHttpHeaders;

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
