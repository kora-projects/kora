package ru.tinkoff.kora.http.server.symbol.processor.controllers

import kotlinx.coroutines.delay
import org.reactivestreams.Publisher
import reactor.util.function.Tuple2
import ru.tinkoff.kora.http.common.HttpHeaders
import ru.tinkoff.kora.http.common.HttpMethod
import ru.tinkoff.kora.http.common.annotation.HttpRoute
import ru.tinkoff.kora.http.server.common.HttpServerRequest
import ru.tinkoff.kora.http.server.common.HttpServerResponse
import ru.tinkoff.kora.http.server.common.SimpleHttpServerResponse
import ru.tinkoff.kora.http.server.common.annotation.HttpController
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

@HttpController
open class TestControllerWithDifferentTypes {
    @HttpRoute(method = HttpMethod.GET, path = "/")
    open suspend fun getRoot(): SimpleHttpServerResponse {
        val response = SimpleHttpServerResponse(
            200,
            "text/plain",
            HttpHeaders.of(),
            StandardCharsets.UTF_8.encode("Hello world")
        )
        delay(1)
        return response
    }

    @HttpRoute(method = HttpMethod.GET, path = "/somePage")
    open suspend fun getSomePage(httpServerRequest: HttpServerRequest?): HttpServerResponse {
        val response = SimpleHttpServerResponse(200, "text/plain", HttpHeaders.of(), StandardCharsets.UTF_8.encode("Hello world"))
        delay(1000)

        return  response
    }

    @HttpRoute(method = HttpMethod.POST, path = "/someEntity")
    open suspend fun postSomeEntityById(someEntity: SomeEntity): SomeEntity {
        delay(1)
        return someEntity
    }

    @HttpRoute(method = HttpMethod.PUT, path = "/someEntityBlocking")
    open fun postSomeEntity(someEntity: SomeEntity): SomeEntity {
        return someEntity
    }

    @HttpRoute(method = HttpMethod.PATCH, path = "/someComplexGenericEntity")
    open fun postSomeEntities(someEntity: List<List<Tuple2<List<String?>?, List<SomeEntity?>>>>): SomeEntity? {
        return someEntity[0][0].t2[0]
    }

    @HttpRoute(method = HttpMethod.DELETE, path = "/deleteByteArrayVoidResult")
    fun deleteByteArrayVoidResult(data: ByteArray?) {
    }

    @HttpRoute(method = HttpMethod.DELETE, path = "/deleteByteArrayMonoVoidResult")
    open suspend fun deleteByteArrayMonoVoidResult(data: ByteArray?) {
        delay(1)
    }

    @HttpRoute(method = HttpMethod.GET, path = "/getWithPrimitiveInt")
    open fun getWithPrimitiveInt(data: Int): Int {
        return data
    }

    @HttpRoute(method = HttpMethod.OPTIONS, path = "/publisherVoid")
    open suspend fun postSomeFilePublisherResult(data: ByteArray?) {
        delay(100)
    }

    @HttpRoute(method = HttpMethod.POST, path = "/publisherByteBuffer")
    open suspend fun postSomeFilePublisherResult(data: Publisher<ByteBuffer?>?) {
        delay(100)
    }
}
