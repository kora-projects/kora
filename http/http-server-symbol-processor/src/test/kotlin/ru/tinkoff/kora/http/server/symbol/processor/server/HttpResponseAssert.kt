package ru.tinkoff.kora.http.server.symbol.processor.server

import reactor.core.publisher.Flux
import org.assertj.core.api.AbstractByteArrayAssert
import org.assertj.core.api.Assertions
import reactor.core.publisher.Mono
import ru.tinkoff.kora.http.common.HttpHeaders
import ru.tinkoff.kora.http.server.common.HttpServerResponse
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.*

class HttpResponseAssert(httpResponse: HttpServerResponse) {
    private val code: Int
    private val contentLength: Int
    private val contentType: String
    private val headers: HttpHeaders
    private val body: ByteArray

    init {
        this.code = httpResponse.code()
        contentLength = httpResponse.contentLength()
        contentType = httpResponse.contentType()
        headers = httpResponse.headers()
        body = Flux.from(httpResponse.body())
            .reduce(ByteArray(0)) { bytes: ByteArray, byteBuffer: ByteBuffer? ->
                val newArr = Arrays.copyOf(bytes, bytes.size + byteBuffer!!.remaining())
                byteBuffer[newArr, bytes.size, byteBuffer.remaining()]
                newArr
            }
            .switchIfEmpty(Mono.just(ByteArray(0)))
            .block()
    }

    fun verifyStatus(expected: Int): HttpResponseAssert {
        Assertions.assertThat(this.code)
            .withFailMessage(
                "Expected response code %d, got %d(%s)",
                expected,
                this.code,
                String(body, StandardCharsets.UTF_8)
            )
            .isEqualTo(expected)
        return this
    }

    fun verifyContentLength(expected: Int): HttpResponseAssert {
        Assertions.assertThat(contentLength)
            .withFailMessage("Expected response body length %d, got %d", contentLength, expected)
            .isEqualTo(expected)
        return this
    }

    fun verifyBody(expected: ByteArray?): HttpResponseAssert {
        Assertions.assertThat(body)
            .withFailMessage {
                val expectedBase64 = Base64.getEncoder().encodeToString(expected).indent(4)
                val gotBase64 = Base64.getEncoder().encodeToString(body).indent(4)
                "Expected response body: \n%s\n\n\tgot: \n%s".formatted(expectedBase64, gotBase64)
            }
            .isEqualTo(expected)
        return this
    }

    fun verifyBody(expected: String): HttpResponseAssert {
        val bodyString = String(body, StandardCharsets.UTF_8)
        Assertions.assertThat(bodyString)
            .withFailMessage {
                "Expected response body: \n%s\n\n\tgot: \n%s".formatted(
                    expected.indent(4),
                    bodyString.indent(4)
                )
            }
            .isEqualTo(expected)
        return this
    }

    fun verifyBody(): AbstractByteArrayAssert<*> {
        return Assertions.assertThat(body)
    }
}
