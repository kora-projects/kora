package ru.tinkoff.kora.json.module.http.server;

import ru.tinkoff.kora.common.util.ByteBufferInputStream;
import ru.tinkoff.kora.common.util.FlowUtils;
import ru.tinkoff.kora.http.server.common.HttpServerRequest;
import ru.tinkoff.kora.http.server.common.HttpServerResponseException;
import ru.tinkoff.kora.http.server.common.handler.HttpServerRequestMapper;
import ru.tinkoff.kora.json.common.JsonReader;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class JsonReaderAsyncHttpServerRequestMapper<T> implements HttpServerRequestMapper<CompletionStage<T>> {
    private final JsonReader<T> reader;

    public JsonReaderAsyncHttpServerRequestMapper(JsonReader<T> reader) {
        this.reader = reader;
    }

    @Override
    public CompletionStage<T> apply(HttpServerRequest request) {
        var body = request.body();
        var fullContent = body.getFullContentIfAvailable();
        if (fullContent != null) {
            try (body) {
                if (fullContent.hasArray()) {
                    return CompletableFuture.completedFuture(this.reader.read(fullContent.array(), fullContent.arrayOffset(), fullContent.remaining()));
                } else {
                    return CompletableFuture.completedFuture(this.reader.read(new ByteBufferInputStream(fullContent)));
                }
            } catch (IOException e) {
                return CompletableFuture.failedFuture(HttpServerResponseException.of(e, 400, e.getMessage()));
            }
        }
        return FlowUtils.toByteArrayFuture(request.body())
            .thenApply(bytes -> {
                try {
                    return this.reader.read(bytes);
                } catch (Exception e) {
                    throw HttpServerResponseException.of(e, 400, e.getMessage());
                }
            });
    }
}
