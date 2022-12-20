package ru.tinkoff.kora.http.server.annotation.processor.controller;

import reactor.core.publisher.Mono;
import ru.tinkoff.kora.http.common.annotation.HttpRoute;
import ru.tinkoff.kora.http.common.annotation.Query;
import ru.tinkoff.kora.http.server.common.HttpServerResponseEntity;
import ru.tinkoff.kora.http.server.common.annotation.HttpController;

import static ru.tinkoff.kora.http.common.HttpMethod.GET;

@HttpController
public class TestControllerWithResponseEntity {

    @HttpRoute(method = GET, path = "/test")
    public HttpServerResponseEntity<String> test(@Query("code") int code) {
        return new HttpServerResponseEntity<>(code, Integer.toString(code));
    }


    @HttpRoute(method = GET, path = "/test2")
    public Mono<HttpServerResponseEntity<String>> mono(@Query("code") int code) {
        return Mono.just(new HttpServerResponseEntity<>(code, Integer.toString(code)));
    }

    @HttpRoute(method = GET, path = "/test3")
    public HttpServerResponseEntity<byte[]> byteArray(@Query("code") int code) {
        return new HttpServerResponseEntity<>(code, new byte[]{});
    }
}
