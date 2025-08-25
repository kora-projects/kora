package ru.tinkoff.kora.http.server.common.auth;

import ru.tinkoff.kora.common.Principal;
import ru.tinkoff.kora.http.server.common.HttpServerRequest;

import jakarta.annotation.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

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
 * public final class UserContextExtractor implements HttpServerPrincipalExtractor<UserContext> {
 *
 *     @Override
 *     public UserContext extract(HttpServerRequest request, @Nullable String value) {
 *         if (value == null) {
 *             throw new IllegalAccessException("No token");
 *         }
 *
 *         final String traceId = request.headers().getFirst("x-trace-id");
 *         final String userId = someAuthService.getUserId(value);
 *         return new UserContext(userId, traceId);
 *     }
 * }
 * }
 * </pre>
 * @see Principal
 */
public interface HttpServerPrincipalExtractor<T extends Principal> {
    T extract(HttpServerRequest request, @Nullable String value);
}
