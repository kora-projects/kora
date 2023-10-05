package ru.tinkoff.kora.http.server.common;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.http.common.MutableHttpHeaders;
import ru.tinkoff.kora.http.common.body.HttpBodyOutput;

public record SimpleHttpServerResponse(int code, MutableHttpHeaders headers, @Nullable HttpBodyOutput body) implements HttpServerResponse {
}
