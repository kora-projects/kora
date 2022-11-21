package ru.tinkoff.kora.http.server.common;

import reactor.core.publisher.Mono;

public interface HttpServerRequestHandler {
    String method();

    String routeTemplate();

    Mono<HttpServerResponse> handle(HttpServerRequest request);
}
