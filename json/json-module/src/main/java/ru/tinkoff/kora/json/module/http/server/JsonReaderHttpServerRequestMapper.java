package ru.tinkoff.kora.json.module.http.server;

import ru.tinkoff.kora.common.util.ByteBufferInputStream;
import ru.tinkoff.kora.http.server.common.HttpServerRequest;
import ru.tinkoff.kora.http.server.common.HttpServerResponseException;
import ru.tinkoff.kora.http.server.common.handler.HttpServerRequestMapper;
import ru.tinkoff.kora.json.common.JsonReader;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

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
                if (is != null) {
                    return this.reader.read(is);
                }
            }
            try {
                var bytes = body.asArrayStage().toCompletableFuture().get();
                return this.reader.read(bytes);
            } catch (InterruptedException e) {
                throw HttpServerResponseException.of(500, e);
            } catch (ExecutionException e) {
                throw HttpServerResponseException.of(500, e.getCause());
            }
        }
    }
}
