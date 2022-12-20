package ru.tinkoff.kora.http.server.symbol.processor.controllers

import ru.tinkoff.kora.http.common.HttpMethod
import ru.tinkoff.kora.http.common.annotation.Header
import ru.tinkoff.kora.http.common.annotation.HttpRoute
import ru.tinkoff.kora.http.server.common.annotation.HttpController

@HttpController
open class TestControllerHeaderParameters {
    /*
    Headers: String / List<String>
     */
    @HttpRoute(method = HttpMethod.GET, path = "/headerString")
    open fun headerString(@Header(value = "string-header") string: String): String {
        return string
    }

    @HttpRoute(method = HttpMethod.GET, path = "/headerNullableString")
    open fun headerNullableString(@Header string: String?) {
    }

    @HttpRoute(method = HttpMethod.GET, path = "/headerStringList")
    open fun headerNullableString(@Header string: List<String>?) {
    }

    @HttpRoute(method = HttpMethod.GET, path = "/headerInt")
    open fun headerInt(@Header(value = "int-header") intHeader: Int): Int {
        val intx2 = intHeader*2
        return intHeader
    }
}
