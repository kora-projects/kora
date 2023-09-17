package ru.tinkoff.kora.http.client

import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking
import ru.tinkoff.kora.common.Context
import ru.tinkoff.kora.http.client.common.HttpClient
import ru.tinkoff.kora.http.client.common.ResponseWithBody
import ru.tinkoff.kora.http.client.common.request.HttpClientRequest

fun call(client: HttpClient, request: HttpClientRequest): ResponseWithBody = runBlocking(Context.Kotlin.asCoroutineContext(Context.current())) {
    val clientRs = client.execute(request).await()
    clientRs.use { clientRs ->
        val body = clientRs.body().collectArray().await()
        ResponseWithBody(clientRs, body)
    }
}
