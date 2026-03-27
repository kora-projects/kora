package io.koraframework.http.server.common.response;

import org.jspecify.annotations.Nullable;
import io.koraframework.common.Mapping;
import io.koraframework.http.server.common.request.HttpServerRequest;

import java.io.IOException;

/**
 * <b>Русский</b>: Контракт обработчика определенного типа данных в HTTP ответ
 * <hr>
 * <b>English</b>: Contract the handler of a particular data type in an HTTP response
 * <br>
 * <br>
 * Пример / Example:
 * <pre>
 * {@code
 * public final class StringServerResponseMapper implements HttpServerResponseMapper<String> {
 *
 *     @Override
 *     public HttpServerResponse apply(HttpServerRequest request, @Nullable String result) throws IOException {
 *         return HttpServerResponse.of(200, HttpBody.plaintext(result));
 *     }
 * }
 * }
 * </pre>
 */
public interface HttpServerResponseMapper<T> extends Mapping.MappingFunction {

    HttpServerResponse apply(HttpServerRequest request, @Nullable T result) throws IOException;
}
