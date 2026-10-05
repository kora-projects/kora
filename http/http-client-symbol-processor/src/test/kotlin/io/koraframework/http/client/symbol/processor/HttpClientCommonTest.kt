package io.koraframework.http.client.symbol.processor

import io.koraframework.http.client.common.request.HttpClientParameterWriter
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import io.koraframework.common.annotation.Component
import io.koraframework.http.client.common.request.HttpClientRequestMapper
import io.koraframework.http.client.common.response.HttpClientResponseMapper
import io.koraframework.logging.common.annotation.Log
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.reset
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.util.UUID
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

    @Test
    fun testOverloadedMethodsAreRejected() {
        val result = compile0(
            listOf(HttpClientSymbolProcessorProvider()), """
            @HttpClient
            interface TestClient {
              @HttpRoute(method = "GET", path = "/a")
              fun get()
              @HttpRoute(method = "GET", path = "/b/{id}")
              fun get(@Path id: String)
            }
            """.trimIndent()
        ).assertFailure()

        assertThat(result.messages).anySatisfy {
            assertThat(it).contains("HTTP client methods can't be overloaded").contains("get")
        }
    }

    @Test
    fun testMethodsNamedAsConstructorParameters() {
        val client = compile(
            listOf<Any>(), """
            @HttpClient
            interface TestClient {
              @HttpRoute(method = "GET", path = "/config")
              fun config()
              @HttpRoute(method = "GET", path = "/httpClient")
              fun httpClient()
              @HttpRoute(method = "GET", path = "/telemetryFactory")
              fun telemetryFactory()
            }
            """.trimIndent()
        )

        onRequest("GET", "http://test-url:8080/config") { rs -> rs.withCode(200) }
        client.invoke<Unit>("config")
        Mockito.verify(httpClient).execute(Mockito.argThat { it.uri().toString() == "http://test-url:8080/config" })
        reset(httpClient)
        onRequest("GET", "http://test-url:8080/httpClient") { rs -> rs.withCode(200) }
        client.invoke<Unit>("httpClient")
        Mockito.verify(httpClient).execute(Mockito.argThat { it.uri().toString() == "http://test-url:8080/httpClient" })
        reset(httpClient)
        onRequest("GET", "http://test-url:8080/telemetryFactory") { rs -> rs.withCode(200) }
        client.invoke<Unit>("telemetryFactory")
        Mockito.verify(httpClient).execute(Mockito.argThat { it.uri().toString() == "http://test-url:8080/telemetryFactory" })
    }

    @Test
    fun testMethodsNamedAsConfigPropertiesAreRejected() {
        for (name in listOf("url", "telemetry", "requestTimeout")) {
            val result = compile0(
                listOf(HttpClientSymbolProcessorProvider()), """
                @HttpClient
                interface TestClient {
                  @HttpRoute(method = "GET", path = "/test")
                  fun $name()
                }
                """.trimIndent()
            ).assertFailure()

            assertThat(result.messages).describedAs(name).anySatisfy {
                assertThat(it).contains("HTTP client method name is reserved").contains(name)
            }
        }
    }

    @Test
    fun testParameterConverterNamesDoNotCollide() {
        val writer = HttpClientParameterWriter<Any> { it.toString() }
        val client = compile(
            listOf<Any>(writer, writer), """
            @HttpClient
            interface TestClient {
              @HttpRoute(method = "GET", path = "/u/{id}")
              fun findUser(@Path("id") id: java.util.UUID)
              @HttpRoute(method = "GET", path = "/u")
              fun find(@Query("userId") userId: java.math.BigDecimal)
            }
            """.trimIndent()
        )

        val id = UUID.randomUUID()
        onRequest("GET", "http://test-url:8080/u/$id") { rs -> rs.withCode(200) }
        client.invoke<Unit>("findUser", id)
        Mockito.verify(httpClient).execute(Mockito.argThat { it.uri().toString() == "http://test-url:8080/u/$id" })
        reset(httpClient)
        onRequest("GET", "http://test-url:8080/u?userId=10") { rs -> rs.withCode(200) }
        client.invoke<Unit>("find", BigDecimal.TEN)
        Mockito.verify(httpClient).execute(Mockito.argThat { it.uri().toString() == "http://test-url:8080/u?userId=10" })
    }
}
