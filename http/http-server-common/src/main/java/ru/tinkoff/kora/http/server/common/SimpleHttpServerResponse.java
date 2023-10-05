package ru.tinkoff.kora.http.server.common;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.http.common.MutableHttpHeaders;
import ru.tinkoff.kora.http.common.body.HttpOutBody;

public record SimpleHttpServerResponse(int code, MutableHttpHeaders headers, @Nullable HttpOutBody body) implements HttpServerResponse {
}
