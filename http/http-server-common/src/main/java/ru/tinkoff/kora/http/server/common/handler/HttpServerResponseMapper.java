package ru.tinkoff.kora.http.server.common.handler;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.common.Mapping;
import ru.tinkoff.kora.http.server.common.HttpServerRequest;
import ru.tinkoff.kora.http.server.common.HttpServerResponse;

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
