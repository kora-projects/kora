package ru.tinkoff.kora.http.server.symbol.processor.controllers

import kotlinx.coroutines.delay
import kotlinx.coroutines.reactor.mono
import ru.tinkoff.kora.http.common.HttpMethod.GET
import ru.tinkoff.kora.http.common.annotation.Header
import ru.tinkoff.kora.http.common.annotation.HttpRoute
import ru.tinkoff.kora.http.common.annotation.Query
import ru.tinkoff.kora.http.server.common.annotation.HttpController
import kotlin.coroutines.CoroutineContext

@HttpController
open class TestControllerKotlinSuspendHandler {
    @HttpRoute(method = GET, path = "/kotlinNonNullableHeader")
    open fun kotlinNonNullableHeader(@Header(value = "str") string: String): String {
        return string
    }

    @HttpRoute(method = GET, path = "/kotlinNullableHeader")
    open fun kotlinNullableHeader(@Header(value = "str") string: String?): String {
        return string ?: "placeholder"
    }

    @HttpRoute(method = GET, path = "/kotlinNullableQuery")
    open fun kotlinNullableQuery(@Query(value = "str") string: String?): String {
        if (string == null) {
            return "placeholder"
        }
        return string
    }

    @HttpRoute(method = GET, path = "/kotlinSuspendHandler")
    open suspend fun kotlinSuspendHandler(@Query("query") query: String?): Int {
        delay(10)
        return 44
    }
}
