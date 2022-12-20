package ru.tinkoff.kora.http.server.symbol.processor.controllers

import reactor.core.publisher.Mono
import ru.tinkoff.kora.common.Mapping
import ru.tinkoff.kora.http.common.HttpMethod
import ru.tinkoff.kora.http.common.annotation.HttpRoute
import ru.tinkoff.kora.http.server.common.HttpServerRequest
import ru.tinkoff.kora.http.server.common.HttpServerResponseException
import ru.tinkoff.kora.http.server.common.annotation.HttpController
import ru.tinkoff.kora.http.server.common.handler.HttpServerRequestMapper

@HttpController
open class MultipleParamsController {

    class Param1
    class Param2
    class Param1Mapping : HttpServerRequestMapper<Param1?> {
        override fun apply(request: HttpServerRequest): Mono<Param1?> {
            if (request.headers().getFirst("Test-Header") == null) {
                throw HttpServerResponseException.of(
                    400,
                    "TEST"
                )
            }
            return Mono.just(Param1())
        }
    }

    class Param2Mapping : HttpServerRequestMapper<Param2?> {
        override fun apply(request: HttpServerRequest): Mono<Param2?> {
            return Mono.just(Param2())
        }
    }

    @HttpRoute(method = HttpMethod.POST, path = "/path")
    open fun someMethodWithParam(
        @Mapping(
            Param1Mapping::class
        ) param1: Param1?, @Mapping(
            Param2Mapping::class
        ) param2: Param2?
    ) {
    }
}
