package io.koraframework.http.server.common.request;

import org.jspecify.annotations.Nullable;
import io.koraframework.common.annotation.Mapping;

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
 *         try (var body = request.body()) {
 *             var content = body.getFullContentIfAvailable();
 *             if (content != null) {
 *                 return content;
 *             }
 *             try (var is = body.asInputStream()) {
 *                 return ByteBuffer.wrap(is.readAllBytes());
 *             }
 *         }
 *     }
 * }
 * }
 * </pre>
 */
public interface HttpServerRequestMapper<T> extends Mapping.MappingFunction {

    @Nullable
    T apply(HttpServerRequest request) throws Exception;
}
