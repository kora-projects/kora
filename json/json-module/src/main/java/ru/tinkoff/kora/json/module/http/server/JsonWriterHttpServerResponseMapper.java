package ru.tinkoff.kora.json.module.http.server;

import ru.tinkoff.kora.http.server.common.HttpServerRequest;
import ru.tinkoff.kora.http.server.common.HttpServerResponse;
import ru.tinkoff.kora.http.server.common.handler.HttpServerResponseMapper;
import ru.tinkoff.kora.json.common.JsonWriter;
import ru.tinkoff.kora.json.module.http.JsonHttpBodyOutput;

public class JsonWriterHttpServerResponseMapper<T> implements HttpServerResponseMapper<T> {
    private final JsonWriter<T> writer;

    public JsonWriterHttpServerResponseMapper(JsonWriter<T> writer) {
        this.writer = writer;
    }

    @Override
    public HttpServerResponse apply(HttpServerRequest request, T value) {
        return HttpServerResponse.of(200, new JsonHttpBodyOutput<>(this.writer, value));
    }
}
