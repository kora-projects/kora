package ru.tinkoff.kora.http.server.common;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.http.common.HttpHeaders;
import ru.tinkoff.kora.http.common.body.HttpBody;
import ru.tinkoff.kora.http.common.body.HttpOutBody;

public interface HttpServerResponse {
    int code();

    HttpHeaders headers();

    @Nullable
    HttpOutBody body();

    static HttpServerResponse of(int code) {
        return new SimpleHttpServerResponse(code, HttpHeaders.of(), null);
    }

    static HttpServerResponse of(int code, @Nullable HttpOutBody body) {
        return new SimpleHttpServerResponse(code, HttpHeaders.of(), body);
    }

    static HttpServerResponse of(int code, @Nullable HttpHeaders headers, HttpOutBody body) {
        return new SimpleHttpServerResponse(code, headers, body);
    }

    static HttpServerResponse of(int code, @Nullable HttpHeaders headers) {
        return new SimpleHttpServerResponse(code, headers, HttpBody.empty());
    }
}
