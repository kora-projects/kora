package ru.tinkoff.kora.http.server.symbol.processor.controllers

import ru.tinkoff.kora.http.common.HttpMethod
import ru.tinkoff.kora.http.common.annotation.HttpRoute
import ru.tinkoff.kora.http.server.common.annotation.HttpController

@HttpController("/root")
open class TestControllerWithPrefix {
    @HttpRoute(method = HttpMethod.GET, path = "/test")
    open fun test(): String {
        return ""
    }

    @HttpRoute(method = HttpMethod.POST, path = "")
    open fun testRoot(): String {
        return ""
    }
}
