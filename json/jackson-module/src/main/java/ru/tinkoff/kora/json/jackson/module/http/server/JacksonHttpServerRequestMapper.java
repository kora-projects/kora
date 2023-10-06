package ru.tinkoff.kora.json.jackson.module.http.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import ru.tinkoff.kora.common.util.ByteBufferInputStream;
import ru.tinkoff.kora.http.server.common.HttpServerRequest;
import ru.tinkoff.kora.http.server.common.HttpServerResponseException;
import ru.tinkoff.kora.http.server.common.handler.HttpServerRequestMapper;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.concurrent.ExecutionException;

public final class JacksonHttpServerRequestMapper<T> implements HttpServerRequestMapper<T> {
    private final ObjectReader objectMapper;

    public JacksonHttpServerRequestMapper(ObjectMapper objectMapper, Type type) {
        this.objectMapper = objectMapper.readerFor(objectMapper.constructType(type));
    }

    @Override
    public T apply(HttpServerRequest request) throws IOException {
        try (var body = request.body()) {
            var fullContent = body.getFullContentIfAvailable();
            if (fullContent != null) {
                if (fullContent.hasArray()) {
                    return this.objectMapper.readValue(fullContent.array(), fullContent.arrayOffset(), fullContent.remaining());
                } else {
                    return this.objectMapper.readValue(new ByteBufferInputStream(fullContent));
                }
            }
            try (var is = body.asInputStream()) {
                if (is != null) {
                    return this.objectMapper.readValue(is);
                }
            }
            try {
                var bytes = body.asArrayStage().toCompletableFuture().get();
                return this.objectMapper.readValue(bytes);
            } catch (InterruptedException e) {
                throw HttpServerResponseException.of(500, e);
            } catch (ExecutionException e) {
                throw HttpServerResponseException.of(500, e.getCause());
            }
        }
    }
}
