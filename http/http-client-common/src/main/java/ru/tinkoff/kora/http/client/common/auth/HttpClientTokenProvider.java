package ru.tinkoff.kora.http.client.common.auth;

import reactor.core.publisher.Mono;
import ru.tinkoff.kora.http.client.common.request.HttpClientRequest;

public interface HttpClientTokenProvider {
    Mono<String> getToken(HttpClientRequest request);
}
