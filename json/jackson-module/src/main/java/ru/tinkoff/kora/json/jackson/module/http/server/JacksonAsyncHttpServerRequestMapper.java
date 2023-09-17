package ru.tinkoff.kora.json.jackson.module.http.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import ru.tinkoff.kora.common.util.ByteBufferInputStream;
import ru.tinkoff.kora.common.util.FlowUtils;
import ru.tinkoff.kora.http.server.common.HttpServerRequest;
import ru.tinkoff.kora.http.server.common.HttpServerResponseException;
import ru.tinkoff.kora.http.server.common.handler.HttpServerRequestMapper;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class JacksonAsyncHttpServerRequestMapper<T> implements HttpServerRequestMapper<CompletionStage<T>> {
    private final ObjectReader objectMapper;

    public JacksonAsyncHttpServerRequestMapper(ObjectMapper objectMapper, Type type) {
        this.objectMapper = objectMapper.readerFor(objectMapper.constructType(type));
    }

    @Override
    public CompletionStage<T> apply(HttpServerRequest request) {
        var body = request.body();
        var fullContent = body.getFullContentIfAvailable();
        if (fullContent != null) {
            try (body) {
                if (fullContent.hasArray()) {
                    return CompletableFuture.completedFuture(this.objectMapper.readValue(fullContent.array(), fullContent.arrayOffset(), fullContent.remaining()));
                } else {
                    return CompletableFuture.completedFuture(this.objectMapper.readValue(new ByteBufferInputStream(fullContent)));
                }
            } catch (IOException e) {
                return CompletableFuture.failedFuture(HttpServerResponseException.of(e, 400, e.getMessage()));
            }
        }
        return FlowUtils.toByteArrayFuture(request.body())
            .thenApply(bytes -> {
                try {
                    return this.objectMapper.readValue(bytes);
                } catch (Exception e) {
                    throw HttpServerResponseException.of(e, 400, e.getMessage());
                }
            });
    }
}
