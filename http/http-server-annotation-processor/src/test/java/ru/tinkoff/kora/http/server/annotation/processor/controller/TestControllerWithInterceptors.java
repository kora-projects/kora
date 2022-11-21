package ru.tinkoff.kora.http.server.annotation.processor.controller;

import reactor.core.publisher.Mono;
import ru.tinkoff.kora.common.Tag;
import ru.tinkoff.kora.http.common.annotation.HttpRoute;
import ru.tinkoff.kora.http.common.annotation.InterceptWith;
import ru.tinkoff.kora.http.server.common.HttpServerInterceptor;
import ru.tinkoff.kora.http.server.common.HttpServerRequest;
import ru.tinkoff.kora.http.server.common.HttpServerResponse;
import ru.tinkoff.kora.http.server.common.annotation.HttpController;

import java.util.function.Function;

import static ru.tinkoff.kora.http.common.HttpMethod.GET;

@HttpController
@InterceptWith(TestControllerWithInterceptors.TestHttpServerInterceptor1.class)
@InterceptWith(value = TestControllerWithInterceptors.TestHttpServerInterceptor1.class, tag = @Tag(TestControllerWithInterceptors.class))
public class TestControllerWithInterceptors {
    public static class TestHttpServerInterceptor1 implements HttpServerInterceptor {
        @Override
        public Mono<HttpServerResponse> intercept(HttpServerRequest request, Function<HttpServerRequest, Mono<HttpServerResponse>> chain) {
            return chain.apply(request);
        }
    }

    public static class TestHttpServerInterceptor2 implements HttpServerInterceptor {
        @Override
        public Mono<HttpServerResponse> intercept(HttpServerRequest request, Function<HttpServerRequest, Mono<HttpServerResponse>> chain) {
            return chain.apply(request);
        }
    }

    @InterceptWith(TestControllerWithInterceptors.TestHttpServerInterceptor1.class)
    @InterceptWith(TestControllerWithInterceptors.TestHttpServerInterceptor2.class)
    @InterceptWith(value = TestControllerWithInterceptors.TestHttpServerInterceptor2.class, tag = @Tag(TestControllerWithInterceptors.class))
    @HttpRoute(method = GET, path = "/withMethodLevelInterceptors")
    public void withMethodLevelInterceptors() {
    }

    @HttpRoute(method = GET, path = "/withoutMethodLevelInterceptors")
    public void withoutMethodLevelInterceptors() {
    }

}
