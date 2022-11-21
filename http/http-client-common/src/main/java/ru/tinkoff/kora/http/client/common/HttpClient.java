package ru.tinkoff.kora.http.client.common;

import reactor.core.publisher.Mono;
import ru.tinkoff.kora.http.client.common.interceptor.HttpClientInterceptor;
import ru.tinkoff.kora.http.client.common.request.HttpClientRequest;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponse;

public interface HttpClient {
    /**
     * Result Mono can throw wrapped {@link HttpClientException}
     */
    Mono<HttpClientResponse> execute(HttpClientRequest request);

    default HttpClient with(HttpClientInterceptor interceptor) {
        return request -> interceptor.processRequest(this::execute, request);
    }
}
