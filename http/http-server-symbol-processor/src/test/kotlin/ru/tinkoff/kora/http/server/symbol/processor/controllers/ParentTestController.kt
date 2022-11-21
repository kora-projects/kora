package ru.tinkoff.kora.http.server.symbol.processor.controllers

import ru.tinkoff.kora.http.common.HttpMethod
import ru.tinkoff.kora.http.common.annotation.HttpRoute

open class ParentTestController<T> {
    @HttpRoute(method = HttpMethod.GET, path = "/parent")
    open fun someMethod(): T? {
        return null
    }

    @HttpRoute(method = HttpMethod.POST, path = "/parent-param")
    open fun someMethodWithParam(param: T) {
    }
}
