package ru.tinkoff.kora.http.server.common;

import ru.tinkoff.kora.http.common.HttpHeaders;
import ru.tinkoff.kora.http.common.body.HttpOutBody;

import jakarta.annotation.Nullable;

public record SimpleHttpServerResponse(int code, HttpHeaders headers, @Nullable HttpOutBody body) implements HttpServerResponse {
}
