package ru.tinkoff.kora.http.server.symbol.processor.controllers

import reactor.core.publisher.Mono
import ru.tinkoff.kora.common.Mapping
import ru.tinkoff.kora.common.Tag
import ru.tinkoff.kora.http.common.HttpHeaders
import ru.tinkoff.kora.http.common.HttpMethod
import ru.tinkoff.kora.http.common.annotation.HttpRoute
import ru.tinkoff.kora.http.server.common.HttpServerRequest
import ru.tinkoff.kora.http.server.common.HttpServerResponse
import ru.tinkoff.kora.http.server.common.SimpleHttpServerResponse
import ru.tinkoff.kora.http.server.common.annotation.HttpController
import ru.tinkoff.kora.http.server.common.handler.HttpServerRequestMapper
import ru.tinkoff.kora.http.server.common.handler.HttpServerResponseMapper
import java.nio.charset.StandardCharsets

@HttpController
open class TestControllerWithMappers {
    @HttpRoute(method = HttpMethod.GET, path = "/queryString")
    @Mapping(
        StringResponseMapper::class
    )
    fun queryString(
        @Mapping(
            StringRequestMapper::class
        ) value: String
    ): String {
        return value
    }

    @HttpRoute(method = HttpMethod.GET, path = "/queryNullableString")
    @Tag(
        TestControllerWithMappers::class
    )
    fun queryNullableString(
        @Mapping(
            StringRequestMapper::class
        ) @Tag(
            TestControllerWithMappers::class
        ) value: String?
    ): String? {
        return value
    }

    class StringRequestMapper : HttpServerRequestMapper<String> {
        override fun apply(request: HttpServerRequest): Mono<String> {
            return Mono.just("test")
        }
    }

    class StringResponseMapper : HttpServerResponseMapper<String> {
        override fun apply(result: String?): Mono<out HttpServerResponse> {
            return Mono.just(
                SimpleHttpServerResponse(
                    200,
                    "text/plain",
                    HttpHeaders.of(),
                    StandardCharsets.UTF_8.encode(result)
                )
            )
        }
    }
}
