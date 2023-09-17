package ru.tinkoff.kora.json.module.http.server;

import ru.tinkoff.kora.common.util.ByteBufferInputStream;
import ru.tinkoff.kora.http.server.common.HttpServerRequest;
import ru.tinkoff.kora.http.server.common.HttpServerResponseException;
import ru.tinkoff.kora.http.server.common.handler.HttpServerRequestMapper;
import ru.tinkoff.kora.json.common.JsonReader;

import java.io.IOException;

public final class JsonReaderHttpServerRequestMapper<T> implements HttpServerRequestMapper<T> {
    private final JsonReader<T> reader;

    public JsonReaderHttpServerRequestMapper(JsonReader<T> reader) {
        this.reader = reader;
    }

    @Override
    public T apply(HttpServerRequest request) {
        try (var body = request.body();) {
            var fullContent = body.getFullContentIfAvailable();
            if (fullContent != null) {
                if (fullContent.hasArray()) {
                    return this.reader.read(fullContent.array(), fullContent.arrayOffset(), fullContent.remaining());
                } else {
                    return this.reader.read(new ByteBufferInputStream(fullContent));
                }
            }
            return this.reader.read(body.getInputStream());
        } catch (IOException e) {
            throw HttpServerResponseException.of(e, 400, e.getMessage());
        }
    }
}
