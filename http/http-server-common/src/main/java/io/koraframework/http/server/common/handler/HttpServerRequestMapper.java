package io.koraframework.http.server.common.handler;

import org.jspecify.annotations.Nullable;
import io.koraframework.common.Mapping;
import io.koraframework.http.server.common.HttpServerRequest;

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
}
