package ru.tinkoff.kora.http.server.symbol.processor.controllers

import ru.tinkoff.kora.http.common.HttpMethod
import ru.tinkoff.kora.http.common.annotation.HttpRoute
import ru.tinkoff.kora.http.server.common.annotation.HttpController

@HttpController("/base")
open class TestControllerWithInheritance : ParentTestController<String?>() {
    @HttpRoute(method = HttpMethod.GET, path = "/child")
    open fun someOtherMethod(): String {
        return "child"
    }
}
