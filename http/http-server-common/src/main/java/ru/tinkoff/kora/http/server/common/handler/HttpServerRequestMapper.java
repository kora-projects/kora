package ru.tinkoff.kora.http.server.common.handler;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.common.Mapping;
import ru.tinkoff.kora.http.server.common.HttpServerRequest;

import java.util.concurrent.CompletionStage;

public interface HttpServerRequestMapper<T> extends Mapping.MappingFunction {
    @Nullable
    T apply(HttpServerRequest request) throws Exception;

    static <T> HttpServerRequestMapper<T> fromAsync(HttpServerRequestMapper<CompletionStage<T>> delegate) {
        return request -> delegate.apply(request).toCompletableFuture().get();
    }
}
