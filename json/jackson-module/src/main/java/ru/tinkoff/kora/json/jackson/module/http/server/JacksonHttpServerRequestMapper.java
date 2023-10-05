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
    public T apply(HttpServerRequest request) {
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
            final byte[] bytes;
            try {
                bytes = body.asArrayStage().toCompletableFuture().get();
            } catch (InterruptedException e) {
                throw HttpServerResponseException.of(e, 400, e.getMessage());
            } catch (ExecutionException e) {
                throw HttpServerResponseException.of(e.getCause(), 400, e.getCause().getMessage());
            }
            return this.objectMapper.readValue(bytes);
        } catch (IOException e) {
            throw HttpServerResponseException.of(e, 400, e.getMessage());
        }
    }
}
