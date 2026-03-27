package io.koraframework.http.server.common.response.mapper;

import org.jspecify.annotations.Nullable;
import io.koraframework.http.common.body.JsonHttpBodyOutput;
import io.koraframework.http.server.common.request.HttpServerRequest;
import io.koraframework.http.server.common.response.HttpServerResponse;
import io.koraframework.http.server.common.response.HttpServerResponseMapper;
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
