package ru.tinkoff.kora.http.server.common.handler;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.common.Mapping;
import ru.tinkoff.kora.http.server.common.HttpServerRequest;
import ru.tinkoff.kora.http.server.common.HttpServerResponse;

import java.io.IOException;

public interface HttpServerResponseMapper<T> extends Mapping.MappingFunction {
    HttpServerResponse apply(Context ctx, HttpServerRequest request, @Nullable T result) throws IOException;
}
