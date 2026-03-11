package io.koraframework.http.client.symbol.processor

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import io.koraframework.common.Component
import io.koraframework.http.client.common.request.HttpClientRequestMapper
import io.koraframework.http.client.common.response.HttpClientResponseMapper
import io.koraframework.logging.common.annotation.Log
import kotlin.reflect.full.declaredFunctions

class HttpClientCommonTest : AbstractHttpClientTest() {

    @Test
    fun testMethodAopAnnotationPreserved() {
        val mapper = Mockito.mock(HttpClientResponseMapper::class.java)
        val client = compile(
            listOf<Any>(mapper), """
            @Component
            @HttpClient
            interface TestClient {
            
              @io.koraframework.logging.common.annotation.Log
              @HttpRoute(method = "POST", path = "/test")
              fun request(): String
            }
            """.trimIndent()
        )

        assertThat(client.objectClass.annotations.any { a -> a is Component }).isTrue
        assertThat(client.objectClass.declaredFunctions.first().annotations.any { a -> a is Log }).isTrue
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
              fun request(@io.koraframework.logging.common.annotation.Log.off arg: String): String
            }
            """.trimIndent()
        )

        assertThat(client.objectClass.annotations.any { a -> a is Component }).isTrue
        assertThat(client.objectClass.declaredFunctions.first().parameters.last().annotations.any { a -> a is Log.off }).isTrue
    }
}
