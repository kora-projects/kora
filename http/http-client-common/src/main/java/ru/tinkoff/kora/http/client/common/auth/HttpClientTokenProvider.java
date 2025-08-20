package ru.tinkoff.kora.http.client.common.auth;

import ru.tinkoff.kora.http.client.common.request.HttpClientRequest;

/**
 * <b>Русский</b>: Интерфейс для предоставление токена авторизации при запроса HTTP клиента
 * <hr>
 * <b>English</b>: Interface for providing an authorization token when requesting an HTTP client
 */
public interface HttpClientTokenProvider {
    String getToken(HttpClientRequest request);
}
