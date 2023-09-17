package ru.tinkoff.kora.http.client.symbol.processor

import ru.tinkoff.kora.http.client.common.response.HttpClientResponse
import ru.tinkoff.kora.http.common.HttpHeaders
import ru.tinkoff.kora.http.common.body.HttpBody
import ru.tinkoff.kora.http.common.body.HttpInBody

data class TestHttpClientResponse(val code: Int = 200, val headers: HttpHeaders = HttpHeaders.of(), val body: HttpInBody = HttpBody.empty()) : HttpClientResponse {
    override fun close() {
    }

    override fun code() = code
    override fun headers() = headers
    override fun body() = body
    fun withCode(code: Int) = copy(code = code)


}
