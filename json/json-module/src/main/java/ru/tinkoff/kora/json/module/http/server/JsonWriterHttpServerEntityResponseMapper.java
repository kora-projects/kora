package ru.tinkoff.kora.json.module.http.server;

import ru.tinkoff.kora.http.common.HttpResponseEntity;
import ru.tinkoff.kora.http.server.common.HttpServerRequest;
import ru.tinkoff.kora.http.server.common.HttpServerResponse;
import ru.tinkoff.kora.http.server.common.handler.HttpServerResponseMapper;
import ru.tinkoff.kora.json.common.JsonWriter;
import ru.tinkoff.kora.json.module.http.JsonHttpBodyOutput;

public final class JsonWriterHttpServerEntityResponseMapper<T> implements HttpServerResponseMapper<HttpResponseEntity<T>> {
    private final JsonWriter<T> writer;

    public JsonWriterHttpServerEntityResponseMapper(JsonWriter<T> writer) {
        this.writer = writer;
    }

    @Override
    public HttpServerResponse apply(HttpServerRequest request, HttpResponseEntity<T> value) {
        return HttpServerResponse.of(value.code(), value.headers(), new JsonHttpBodyOutput<>(this.writer, value.body()));
    }
}
