package ru.tinkoff.kora.http.client.common

import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import ru.tinkoff.kora.common.util.ReactorUtils
import ru.tinkoff.kora.http.client.common.request.HttpClientRequest
import ru.tinkoff.kora.http.client.common.response.HttpClientResponse

suspend fun <T> HttpClientResponse.use(callback: suspend (HttpClientResponse) -> T) = try {
    callback.invoke(this)
} finally {
    this.close().awaitSingleOrNull()
}

suspend fun HttpClientResponse.bodyBytes() = ReactorUtils.toByteArrayMono(this.body()).awaitSingle()!!
suspend fun HttpClientResponse.bodyByteBuffer() = ReactorUtils.toByteBufferMono(this.body()).awaitSingle()!!

suspend fun <T> HttpClient.execute(request: HttpClientRequest, callback: suspend (HttpClientResponse) -> T) = this.execute(request).awaitSingle().use(callback)
