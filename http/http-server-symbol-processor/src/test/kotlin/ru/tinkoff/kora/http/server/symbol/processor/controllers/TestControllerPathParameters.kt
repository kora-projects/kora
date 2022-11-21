package ru.tinkoff.kora.http.server.symbol.processor.controllers

import ru.tinkoff.kora.http.common.HttpMethod
import ru.tinkoff.kora.http.common.annotation.HttpRoute
import ru.tinkoff.kora.http.common.annotation.Path
import ru.tinkoff.kora.http.server.common.annotation.HttpController

@HttpController
open class TestControllerPathParameters {
    @HttpRoute(method = HttpMethod.GET, path = "/pathString/{string}")
    open fun pathString(@Path string: String): String {
        return string
    }

    @HttpRoute(method = HttpMethod.GET, path = "/pathStringWithName/{stringWithName}")
    open fun pathStringWithName(@Path("stringWithName") string: String?) {
    }

    @HttpRoute(method = HttpMethod.GET, path = "/pathInteger/{value}")
    open fun pathInteger(@Path value: Int) {
    }

    @HttpRoute(method = HttpMethod.GET, path = "/pathIntegerObject/{value}")
    open fun pathIntegerObject(@Path value: Int?) {
    }

    @HttpRoute(method = HttpMethod.GET, path = "/pathLong/{value}")
    open fun pathLong(@Path value: Long) {
    }

    @HttpRoute(method = HttpMethod.GET, path = "/pathLongObject/{value}")
    open fun pathLongObject(@Path value: Long?) {
    }

    @HttpRoute(method = HttpMethod.GET, path = "/pathDouble/{value}")
    open fun pathDouble(@Path value: Double) {
    }

    @HttpRoute(method = HttpMethod.GET, path = "/pathDoubleObject/{value}")
    open fun pathDoubleObject(@Path value: Double?) {
    }

    @HttpRoute(method = HttpMethod.GET, path = "/pathEnum/{value}")
    open fun pathEnum(@Path value: TestEnum?) {
    }
}
