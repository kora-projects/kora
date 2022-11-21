package ru.tinkoff.kora.http.server.symbol.processor.controllers

import ru.tinkoff.kora.http.common.HttpMethod
import ru.tinkoff.kora.http.common.annotation.HttpRoute
import ru.tinkoff.kora.http.server.common.annotation.HttpController

@HttpController
open class TestControllerWithPaths {
    @HttpRoute(method = HttpMethod.GET, path = "/swagger.yaml")
    fun swaggerYaml(): String {
        return ""
    }
}
