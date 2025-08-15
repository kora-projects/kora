package ru.tinkoff.kora.http.client.symbol.processor

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import ru.tinkoff.kora.common.Component
import ru.tinkoff.kora.http.client.common.request.HttpClientRequestMapper
import ru.tinkoff.kora.http.client.common.response.HttpClientResponseMapper

class HttpClientCommonTest : AbstractHttpClientTest() {

    @Test
    fun testMethodAopAnnotationPreserved() {
        val mapper = Mockito.mock(HttpClientResponseMapper::class.java)
        val client = compile(
            listOf<Any>(mapper), """
            @Component
            @HttpClient
            interface TestClient {
            
              @ru.tinkoff.kora.validation.common.annotation.Validate
              @HttpRoute(method = "POST", path = "/test")
              fun request(): String
            }
            """.trimIndent()
        )

        assertThat(client.objectClass.annotations.any { a -> a is Component }).isTrue
    }

    @Test
    fun testMethodArgumentsAnnotationPreserved() {
        val requestMapper = Mockito.mock(HttpClientRequestMapper::class.java)
        val responseMapper = Mockito.mock(HttpClientResponseMapper::class.java)
        val client = compile(
            listOf<Any>(requestMapper, responseMapper), """
            @Component
            @HttpClient
            interface TestClient {
            
              @HttpRoute(method = "POST", path = "/test")
              fun request(@ru.tinkoff.kora.validation.common.annotation.NotBlank arg: String): String
            }
            """.trimIndent()
        )

        assertThat(client.objectClass.annotations.any { a -> a is Component }).isTrue
    }
}
