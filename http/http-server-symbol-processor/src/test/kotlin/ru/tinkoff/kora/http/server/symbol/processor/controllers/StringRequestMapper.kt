package ru.tinkoff.kora.http.server.symbol.processor.controllers

import reactor.core.publisher.Mono
import ru.tinkoff.kora.http.server.common.HttpServerRequest
import ru.tinkoff.kora.http.server.common.handler.HttpServerRequestMapper

class StringRequestMapper : HttpServerRequestMapper<String> {
    override fun apply(request: HttpServerRequest): Mono<String> {
        return Mono.just("test")
    }
}
