package ru.tinkoff.kora.http.server.common;

import ru.tinkoff.kora.http.common.HttpHeaders;

public record HttpServerResponseEntity<T>(int code, T body, HttpHeaders headers) {
    public HttpServerResponseEntity(int code, T body) {
        this(code, body, HttpHeaders.of());
    }
}
