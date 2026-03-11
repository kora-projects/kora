package io.koraframework.http.client.symbol.processor

import io.koraframework.http.client.common.response.HttpClientResponse
import io.koraframework.http.common.body.HttpBody
import io.koraframework.http.common.body.HttpBodyInput
import io.koraframework.http.common.header.HttpHeaders

data class TestHttpClientResponse(val code: Int = 200, val headers: HttpHeaders = HttpHeaders.of(), val body: HttpBodyInput = HttpBody.empty()) : HttpClientResponse {
    override fun close() {
    }

    override fun code() = code
    override fun headers() = headers
    override fun body() = body
    fun withCode(code: Int) = copy(code = code)


}
