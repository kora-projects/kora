package io.koraframework.http.server.common.response.mapper;

import io.koraframework.http.common.body.*;
import io.koraframework.http.server.common.request.*;
import io.koraframework.http.server.common.response.*;
import io.koraframework.json.common.*;
import org.jspecify.annotations.*;

public class JsonWriterHttpServerResponseMapper<T> implements HttpServerResponseMapper<T> {

    private final JsonWriter<T> writer;

    public JsonWriterHttpServerResponseMapper(JsonWriter<T> writer) {
        this.writer = writer;
    }

    @Override
    public HttpServerResponse apply(HttpServerRequest request, @Nullable T value) {
        return HttpServerResponse.of(200, new FastJsonHttpBodyOutput<>(this.writer, value));
    }
}
