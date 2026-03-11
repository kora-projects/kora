package io.koraframework.http.server.common.mapper;

import io.koraframework.common.util.ByteBufferInputStream;
import io.koraframework.http.server.common.HttpServerRequest;
import io.koraframework.http.server.common.handler.HttpServerRequestMapper;
import io.koraframework.json.common.JsonReader;

import java.io.IOException;

public final class JsonReaderHttpServerRequestMapper<T> implements HttpServerRequestMapper<T> {
    private final JsonReader<T> reader;

    public JsonReaderHttpServerRequestMapper(JsonReader<T> reader) {
        this.reader = reader;
    }

    @Override
    public T apply(HttpServerRequest request) throws IOException {
        try (var body = request.body()) {
            var fullContent = body.getFullContentIfAvailable();
            if (fullContent != null) {
                if (fullContent.hasArray()) {
                    return this.reader.read(fullContent.array(), fullContent.arrayOffset(), fullContent.remaining());
                } else {
                    return this.reader.read(new ByteBufferInputStream(fullContent));
                }
            }
            try (var is = body.asInputStream()) {
                return this.reader.read(is);
            }
        }
    }
}
