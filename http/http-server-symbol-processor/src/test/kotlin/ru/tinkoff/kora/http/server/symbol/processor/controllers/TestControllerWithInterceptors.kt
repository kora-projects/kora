package ru.tinkoff.kora.http.server.symbol.processor.controllers

import reactor.core.publisher.Mono
import ru.tinkoff.kora.common.Tag
import ru.tinkoff.kora.http.common.HttpMethod
import ru.tinkoff.kora.http.common.annotation.HttpRoute
import ru.tinkoff.kora.http.common.annotation.InterceptWith
import ru.tinkoff.kora.http.server.common.HttpServerInterceptor
import ru.tinkoff.kora.http.server.common.HttpServerRequest
import ru.tinkoff.kora.http.server.common.HttpServerResponse
import ru.tinkoff.kora.http.server.common.annotation.HttpController
import java.util.function.Function


@HttpController
@InterceptWith(TestControllerWithInterceptors.TestHttpServerInterceptor1::class)
@InterceptWith(value = TestControllerWithInterceptors.TestHttpServerInterceptor1::class, tag = Tag(TestControllerWithInterceptors::class))
open class TestControllerWithInterceptors {
    open class TestHttpServerInterceptor1 : HttpServerInterceptor {
        override fun intercept(request: HttpServerRequest, chain: Function<HttpServerRequest, Mono<HttpServerResponse>>): Mono<HttpServerResponse> {
            return chain.apply(request)
        }
    }

    open class TestHttpServerInterceptor2 : HttpServerInterceptor {
        override fun intercept(request: HttpServerRequest, chain: Function<HttpServerRequest, Mono<HttpServerResponse>>): Mono<HttpServerResponse> {
            return chain.apply(request)
        }
    }

    @InterceptWith(TestHttpServerInterceptor1::class)
    @InterceptWith(TestHttpServerInterceptor2::class)
    @InterceptWith(
        value = TestHttpServerInterceptor2::class, tag = Tag(
            TestControllerWithInterceptors::class
        )
    )
    @HttpRoute(method = HttpMethod.GET, path = "/withMethodLevelInterceptors")
    open fun withMethodLevelInterceptors() {
    }

    @HttpRoute(method = HttpMethod.GET, path = "/withoutMethodLevelInterceptors")
    open fun withoutMethodLevelInterceptors(body: String) {
    }
}
