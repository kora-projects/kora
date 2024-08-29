package ru.tinkoff.kora.http.server.common.handler;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.common.Mapping;
import ru.tinkoff.kora.http.server.common.HttpServerRequest;

import java.nio.ByteBuffer;
import java.util.concurrent.CompletionStage;

/**
 * <b>Русский</b>: Контракт обработчика HTTP запроса в определенный тип данных
 * <hr>
 * <b>English</b>: Contract the HTTP request handler to a specific data type
 * <br>
 * <br>
 * Пример / Example:
 * <pre>
 * {@code
 * public final class ByteBufferServerRequestMapper implements HttpServerRequestMapper<ByteBuffer> {
 *
 *     @Nullable
 *     @Override
 *     public ByteBuffer apply(HttpServerRequest request) throws Exception {
 *         return request.body().getFullContentIfAvailable() != null
 *             ? request.body().getFullContentIfAvailable()
 *             : request.body().asBufferStage().toCompletableFuture().join();
 *     }
 * }
 * }
 * </pre>
 */
public interface HttpServerRequestMapper<T> extends Mapping.MappingFunction {

    @Nullable
    T apply(HttpServerRequest request) throws Exception;

    static <T> HttpServerRequestMapper<T> fromAsync(HttpServerRequestMapper<CompletionStage<T>> delegate) {
        return request -> delegate.apply(request).toCompletableFuture().get();
    }
}
