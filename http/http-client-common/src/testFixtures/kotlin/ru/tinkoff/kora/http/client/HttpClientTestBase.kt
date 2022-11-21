package ru.tinkoff.kora.http.client

import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import kotlinx.coroutines.runBlocking
import ru.tinkoff.kora.common.Context
import ru.tinkoff.kora.common.util.ReactorUtils
import ru.tinkoff.kora.http.client.common.HttpClient
import ru.tinkoff.kora.http.client.common.ResponseWithBody
import ru.tinkoff.kora.http.client.common.request.HttpClientRequest

fun call(client: HttpClient, request: HttpClientRequest): ResponseWithBody {
    val r = runBlocking(Context.Kotlin.asCoroutineContext(Context.current())) {
        val clientRs = client.execute(request).awaitSingle()
        try {
            val body = ReactorUtils.toByteArrayMono(clientRs.body()).awaitSingle()
            ResponseWithBody(clientRs, body)
        } finally {
            clientRs.close().awaitSingleOrNull()
        }
    }
    return r
}
