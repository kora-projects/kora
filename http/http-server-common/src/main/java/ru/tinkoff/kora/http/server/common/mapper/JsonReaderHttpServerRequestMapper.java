package ru.tinkoff.kora.http.server.common.mapper;

import org.jspecify.annotations.Nullable;
import ru.tinkoff.kora.common.util.ByteBufferInputStream;
import ru.tinkoff.kora.http.server.common.HttpServerRequest;
import ru.tinkoff.kora.http.server.common.handler.HttpServerRequestMapper;
import ru.tinkoff.kora.json.common.JsonReader;

import java.io.IOException;

public final class JsonReaderHttpServerRequestMapper<T> implements HttpServerRequestMapper<T> {

    private final JsonReader<T> reader;

    public JsonReaderHttpServerRequestMapper(JsonReader<T> reader) {
        this.reader = reader;
    }

    @Nullable
    @Override
    public T apply(HttpServerRequest request) throws IOException {
        try (var body = request.body()) {
            var fullContent = body.getFullContentIfAvailable();
            if (fullContent != null) {
                if (fullContent.remaining() == 0) {
                    return null;
                }
                if (fullContent.hasArray()) {
                    return this.reader.read(fullContent.array(), fullContent.arrayOffset(), fullContent.remaining());
                } else {
                    return this.reader.read(new ByteBufferInputStream(fullContent));
                }
            }
            try (var is = body.asInputStream()) {
                if (is.available() == 0) {
                    return null;
                }
                return this.reader.read(is);
            }
        }
    }
}
