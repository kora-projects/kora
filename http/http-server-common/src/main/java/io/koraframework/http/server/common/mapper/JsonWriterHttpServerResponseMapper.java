package io.koraframework.http.server.common.mapper;

import org.jspecify.annotations.Nullable;
import io.koraframework.http.common.body.JsonHttpBodyOutput;
import io.koraframework.http.server.common.HttpServerRequest;
import io.koraframework.http.server.common.HttpServerResponse;
import io.koraframework.http.server.common.handler.HttpServerResponseMapper;
import io.koraframework.json.common.JsonWriter;

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
