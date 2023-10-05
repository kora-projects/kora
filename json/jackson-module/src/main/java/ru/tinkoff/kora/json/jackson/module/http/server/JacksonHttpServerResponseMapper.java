package ru.tinkoff.kora.json.jackson.module.http.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import ru.tinkoff.kora.application.graph.TypeRef;
import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.http.server.common.HttpServerRequest;
import ru.tinkoff.kora.http.server.common.HttpServerResponse;
import ru.tinkoff.kora.http.server.common.handler.HttpServerResponseMapper;
import ru.tinkoff.kora.json.jackson.module.http.JacksonHttpBodyOutput;

public final class JacksonHttpServerResponseMapper<T> implements HttpServerResponseMapper<T> {
    private final ObjectWriter objectMapper;

    public JacksonHttpServerResponseMapper(ObjectMapper objectMapper, TypeRef<T> typeRef) {
        this.objectMapper = objectMapper.writerFor(objectMapper.constructType(typeRef));
    }

    @Override
    public HttpServerResponse apply(Context ctx, HttpServerRequest request, T result) {
        var body = new JacksonHttpBodyOutput<>(objectMapper, ctx, result);
        return HttpServerResponse.of(200, body);
    }
}
