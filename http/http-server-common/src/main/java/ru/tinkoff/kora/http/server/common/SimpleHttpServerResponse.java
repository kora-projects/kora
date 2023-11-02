package ru.tinkoff.kora.http.server.common;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.http.common.body.HttpBodyOutput;
import ru.tinkoff.kora.http.common.header.MutableHttpHeaders;

public record SimpleHttpServerResponse(int code, MutableHttpHeaders headers, @Nullable HttpBodyOutput body) implements HttpServerResponse {
}
