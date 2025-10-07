package ru.tinkoff.kora.http.server.common.auth;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.common.Principal;
import ru.tinkoff.kora.http.server.common.HttpServerRequest;

/**
 * <b>Русский</b>: Контракт по извлечению контекста авторизации из HTTP запроса
 * <hr>
 * <b>English</b>: Contract for extracting authorization context from HTTP request
 * <br>
 * <br>
 * Пример / Example:
 * <pre>
 * {@code
 * public record UserContext(String userId, String traceId) implements Principal { }
 *
 * public final class UserContextExtractor implements HttpServerPrincipalExtractor<String, UserContext> {
 *
 *     @Override
 *     public UserContext extract(HttpServerRequest request, @Nullable String value) {
 *         if (value == null) {
 *             throw new IllegalAccessException("No token");
 *         }
 *
 *         var traceId = request.headers().getFirst("x-trace-id");
 *         var userId = someAuthService.getUserId(value);
 *         return new UserContext(userId, traceId);
 *     }
 * }
 * }
 * </pre>
 *
 * @see Principal
 */
public interface HttpServerPrincipalExtractor<T, P extends Principal> {
    P extract(HttpServerRequest request, @Nullable T token);
}
