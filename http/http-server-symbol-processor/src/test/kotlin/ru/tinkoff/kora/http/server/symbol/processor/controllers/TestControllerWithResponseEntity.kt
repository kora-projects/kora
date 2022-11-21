package ru.tinkoff.kora.http.server.symbol.processor.controllers

import kotlinx.coroutines.delay
import ru.tinkoff.kora.http.common.HttpMethod
import ru.tinkoff.kora.http.common.annotation.HttpRoute
import ru.tinkoff.kora.http.common.annotation.Query
import ru.tinkoff.kora.http.server.common.annotation.HttpController
import ru.tinkoff.kora.http.server.common.HttpServerResponseEntity

@HttpController
open class TestControllerWithResponseEntity {
    @HttpRoute(method = HttpMethod.GET, path = "/test")
    open fun test(@Query("code") code: Int): HttpServerResponseEntity<String> {
        return HttpServerResponseEntity(code, code.toString())
    }

    @HttpRoute(method = HttpMethod.GET, path = "/test2")
    open suspend fun suspended(@Query("code") code: Int): HttpServerResponseEntity<String> {
        delay(2)
        return HttpServerResponseEntity(code, code.toString())
    }

    @HttpRoute(method = HttpMethod.GET, path = "/test3")
    open fun byteArray(@Query("code") code: Int): HttpServerResponseEntity<ByteArray> {
        return HttpServerResponseEntity(code, byteArrayOf())
    }
}
