package ru.tinkoff.kora.http.server.common.mapper;

import org.jspecify.annotations.Nullable;
import ru.tinkoff.kora.http.common.body.JsonHttpBodyOutput;
import ru.tinkoff.kora.http.server.common.HttpServerRequest;
import ru.tinkoff.kora.http.server.common.HttpServerResponse;
import ru.tinkoff.kora.http.server.common.handler.HttpServerResponseMapper;
import ru.tinkoff.kora.json.common.JsonWriter;

public class JsonWriterHttpServerResponseMapper<T> implements HttpServerResponseMapper<T> {
    private final JsonWriter<T> writer;

    public JsonWriterHttpServerResponseMapper(JsonWriter<T> writer) {
        this.writer = writer;
    }

    @Override
    public HttpServerResponse apply(HttpServerRequest request, @Nullable T value) {
        return HttpServerResponse.of(200, new JsonHttpBodyOutput<>(this.writer, value));
    }
}
