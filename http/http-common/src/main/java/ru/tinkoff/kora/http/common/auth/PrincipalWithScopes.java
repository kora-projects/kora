package ru.tinkoff.kora.http.common.auth;

import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.common.Principal;

import java.util.Collection;

/**
 * <b>Русский</b>: Является интерфейсом контекста авторизации совместно со областью видимости в рамках запроса от HTTP сервера {@link Context}.
 * Реализация авторизации должна наследовать этот интерфейс.
 * <hr>
 * <b>English</b>: Represents interface of the authorisation context with scopes within the request from the HTTP server {@link Context}.
 * An authorisation implementation must inherit this interface.
 *
 * @see Principal
 */
public interface PrincipalWithScopes extends Principal {
    Collection<String> scopes();
}
