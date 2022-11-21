package ru.tinkoff.kora.http.server.common;

import reactor.core.publisher.Mono;

import java.util.function.Function;

public interface HttpServerInterceptor {
    Mono<HttpServerResponse> intercept(HttpServerRequest request, Function<HttpServerRequest, Mono<HttpServerResponse>> chain);

    static HttpServerInterceptor noop() {
        return (request, chain) -> chain.apply(request);
    }
}
