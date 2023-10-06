package ru.tinkoff.kora.http.server.symbol.processor.server

import org.assertj.core.api.AbstractByteArrayAssert
import org.assertj.core.api.Assertions
import ru.tinkoff.kora.common.util.FlowUtils
import ru.tinkoff.kora.http.common.header.HttpHeaders
import ru.tinkoff.kora.http.server.common.HttpServerResponse
import java.nio.charset.StandardCharsets
import java.util.*

class HttpResponseAssert(httpResponse: HttpServerResponse) {
    private val code: Int
    private val contentLength: Int?
    private val contentType: String?
    private val headers: HttpHeaders
    private val body: ByteArray

    init {
        this.code = httpResponse.code()
        contentLength = httpResponse.body()?.contentLength()
        contentType = httpResponse.body()?.contentType()
        headers = httpResponse.headers()
        body = httpResponse.body()
            ?.let { FlowUtils.toByteArrayFuture(it).get() }
            ?: byteArrayOf()
    }

    fun hasStatus(expected: Int): HttpResponseAssert {
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

    fun hasBody(expected: ByteArray?): HttpResponseAssert {
        Assertions.assertThat(body)
            .withFailMessage {
                val expectedBase64 = Base64.getEncoder().encodeToString(expected).indent(4)
                val gotBase64 = Base64.getEncoder().encodeToString(body).indent(4)
                "Expected response body: \n%s\n\n\tgot: \n%s".formatted(expectedBase64, gotBase64)
            }
            .isEqualTo(expected)
        return this
    }

    fun hasBody(expected: String): HttpResponseAssert {
        val bodyString = String(body, StandardCharsets.UTF_8)
        Assertions.assertThat(bodyString)
            .withFailMessage {
                "Expected response body: \n${expected.prependIndent("    ")}\n\n\tgot: \n${bodyString.prependIndent("    ")}"
            }
            .isEqualTo(expected)
        return this
    }

    fun hasBody(): AbstractByteArrayAssert<*> {
        return Assertions.assertThat(body)
    }

    fun hasHeader(key: String, value: String): HttpResponseAssert {
        val actualValue = headers.getFirst(key)
        Assertions.assertThat(actualValue).isEqualTo(value)

        return this
    }
}
