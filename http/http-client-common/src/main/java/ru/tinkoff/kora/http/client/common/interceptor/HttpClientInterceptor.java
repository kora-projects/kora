package ru.tinkoff.kora.http.client.common.interceptor;

import reactor.core.publisher.Mono;
import ru.tinkoff.kora.http.client.common.request.HttpClientRequest;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponse;

import java.util.function.Function;

public interface HttpClientInterceptor {
    Mono<HttpClientResponse> processRequest(Function<HttpClientRequest, Mono<HttpClientResponse>> chain, HttpClientRequest request);

    static HttpClientInterceptor noop() {
        return Function::apply;
    }
}
