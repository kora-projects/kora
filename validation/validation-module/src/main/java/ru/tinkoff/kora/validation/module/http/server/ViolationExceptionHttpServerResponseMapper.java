package ru.tinkoff.kora.validation.module.http.server;

import ru.tinkoff.kora.http.server.common.HttpServerRequest;
import ru.tinkoff.kora.http.server.common.HttpServerResponse;
import ru.tinkoff.kora.validation.common.ViolationException;

import javax.annotation.Nullable;

public interface ViolationExceptionHttpServerResponseMapper {
    @Nullable
    HttpServerResponse apply(HttpServerRequest request, ViolationException exception);
}
