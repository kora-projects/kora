package ru.tinkoff.kora.json.jackson.module.http.server;

import ru.tinkoff.kora.common.util.ByteBufferInputStream;
import ru.tinkoff.kora.http.server.common.HttpServerRequest;
import ru.tinkoff.kora.http.server.common.HttpServerResponseException;
import ru.tinkoff.kora.http.server.common.handler.HttpServerRequestMapper;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectReader;

import java.io.IOException;
import java.lang.reflect.Type;

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
                return this.objectMapper.readValue(is);
            }
        } catch (JacksonException e) {
            throw HttpServerResponseException.of(400, e);
        }
    }
}
