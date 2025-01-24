package ru.tinkoff.kora.http.client.symbol.processor.parameters

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito
import ru.tinkoff.kora.http.client.common.writer.StringParameterConverter
import ru.tinkoff.kora.http.client.symbol.processor.AbstractHttpClientTest
import ru.tinkoff.kora.ksp.common.exception.ProcessingErrorException

class HttpClientQueryParametersTest : AbstractHttpClientTest() {
    @Test
    fun testQueryParam() {
        val client = compile(
            listOf<Any>(), """
            @HttpClient
            interface TestClient {
              @HttpRoute(method = "POST", path = "/test")
              fun request(@Query qParam: String)
            }
            
            """.trimIndent()
        )
        onRequest("POST", "http://test-url:8080/test?qParam=test1") { rs -> rs }
        client.invoke<Unit>("request", "test1")

        Mockito.reset(httpClient)
        onRequest("POST", "http://test-url:8080/test?qParam=test2") { rs -> rs }
        client.invoke<Unit>("request", "test2")
    }

    @Test
    fun testIntParam() {
        val client = compile(
            listOf<Any>(), """
            @HttpClient
            interface TestClient {
              @HttpRoute(method = "POST", path = "/test")
              fun request(@Query qParam: Int)
            }
            
            """.trimIndent()
        )
        onRequest("POST", "http://test-url:8080/test?qParam=10") { rs -> rs }
        client.invoke<Unit>("request", 10)

        Mockito.reset(httpClient)
        onRequest("POST", "http://test-url:8080/test?qParam=20") { rs -> rs }
        client.invoke<Unit>("request", 20)
    }

    @Test
    fun testQueryParamUnknownPathParamFails() {
        assertThrows<Exception> {
            compile(
                listOf<Any>(), """
            @HttpClient
            interface TestClient {
              @HttpRoute(method = "POST", path = "/test/{param}")
              fun request(@Query qParam: Int)
            }
            
            """.trimIndent()
            )
        }
    }

    @Test
    fun testQueryParamIllegalFails() {
        assertThrows<Exception> {
            compile(
                listOf<Any>(), """
            @HttpClient
            interface TestClient {
              @HttpRoute(method = "POST", path = "/test{")
              fun request(@Query qParam: Int)
            }
            
            """.trimIndent()
            )
        }
    }

    @Test
    fun testNullableIntParam() {
        val client = compile(
            listOf<Any>(), """
            @HttpClient
            interface TestClient {
              @HttpRoute(method = "POST", path = "/test")
              fun request(@Query qParam: Int?)
            }
            
            """.trimIndent()
        )
        onRequest("POST", "http://test-url:8080/test?qParam=10") { rs -> rs }
        client.invoke<Unit>("request", 10)

        Mockito.reset(httpClient)
        onRequest("POST", "http://test-url:8080/test") { rs -> rs }
        client.invoke<Unit>("request", null)
    }

    @Test
    fun testListQueryParam() {
        val client = compile(
            listOf<Any>(), """
            @HttpClient
            interface TestClient {
              @HttpRoute(method = "POST", path = "/test")
              fun request(@Query qParam: List<String>)
            }
            
            """.trimIndent()
        )
        onRequest("POST", "http://test-url:8080/test?qParam=test1") { rs -> rs }
        client.invoke<Unit>("request", listOf("test1"))

        Mockito.reset(httpClient)
        onRequest("POST", "http://test-url:8080/test?qParam=test1&qParam=test2") { rs -> rs }
        client.invoke<Unit>("request", listOf("test1", "test2"))
    }

    @Test
    fun testIntListQueryParam() {
        val client = compile(
            listOf<Any>(), """
            @HttpClient
            interface TestClient {
              @HttpRoute(method = "POST", path = "/test")
              fun request(@Query qParam: List<Int>)
            }
            
            """.trimIndent()
        )
        onRequest("POST", "http://test-url:8080/test?qParam=10") { rs -> rs }
        client.invoke<Unit>("request", listOf(10))

        Mockito.reset(httpClient)
        onRequest("POST", "http://test-url:8080/test?qParam=10&qParam=20") { rs -> rs }
        client.invoke<Unit>("request", listOf(10, 20))
    }

    @Test
    fun testSetQueryParam() {
        val client = compile(
            listOf<Any>(), """
            @HttpClient
            interface TestClient {
              @HttpRoute(method = "POST", path = "/test")
              fun request(@Query qParam: Set<String>)
            }
            
            """.trimIndent()
        )
        onRequest("POST", "http://test-url:8080/test?qParam=test1") { rs -> rs }
        client.invoke<Unit>("request", linkedSetOf("test1"))

        Mockito.reset(httpClient)
        onRequest("POST", "http://test-url:8080/test?qParam=test10&qParam=test20") { rs -> rs }
        client.invoke<Unit>("request", linkedSetOf("test10", "test20"))
    }

    @Test
    fun testMapQueryParam() {
        val client = compile(
            listOf<Any>(), """
            @HttpClient
            interface TestClient {
              @HttpRoute(method = "POST", path = "/test")
              fun request(@Query params: Map<String, String>)
            }
            """.trimIndent()
        )

        onRequest("POST", "http://test-url:8080/test?q1=test1") { rs -> rs }
        client.invoke<Unit>("request", mapOf("q1" to "test1"))

        Mockito.reset(httpClient)
        onRequest("POST", "http://test-url:8080/test?q1=test1&q2=test2") { rs -> rs }
        client.invoke<Unit>("request", mapOf("q1" to "test1", "q2" to "test2"))
    }

    @Test
    fun testMapQueryParamWithConverter() {
        val client = compile(
            listOf<Any>(StringParameterConverter<Any> { it.toString() }), """
            @HttpClient
            interface TestClient {
              @HttpRoute(method = "POST", path = "/test")
              fun request(@Query params: Map<String, Any>)
            }
            """.trimIndent()
        )

        onRequest("POST", "http://test-url:8080/test?q1=test1") { rs -> rs }
        client.invoke<Unit>("request", mapOf("q1" to "test1"))

        Mockito.reset(httpClient)
        onRequest("POST", "http://test-url:8080/test?q1=test1&q2=test2") { rs -> rs }
        client.invoke<Unit>("request", mapOf("q1" to "test1", "q2" to "test2"))
    }

    @Test
    fun testMapQueryParamNullable() {
        val client = compile(
            listOf<Any>(), """
            @HttpClient
            interface TestClient {
              @HttpRoute(method = "POST", path = "/test")
              fun request(@Query params: Map<String, String?>)
            }
            """.trimIndent()
        )

        onRequest("POST", "http://test-url:8080/test?q1=test1") { rs -> rs }
        client.invoke<Unit>("request", mapOf("q1" to "test1"))

        Mockito.reset(httpClient)
        onRequest("POST", "http://test-url:8080/test?q1=test1&q2=test2") { rs -> rs }
        client.invoke<Unit>("request", mapOf("q1" to "test1", "q2" to "test2"))
    }

    @Test
    fun testMapQueryParamWithConverterNullable() {
        val client = compile(
            listOf<Any>(StringParameterConverter<Any> { it.toString() }), """
            @HttpClient
            interface TestClient {
              @HttpRoute(method = "POST", path = "/test")
              fun request(@Query params: Map<String, Any?>)
            }
            """.trimIndent()
        )

        onRequest("POST", "http://test-url:8080/test?q1=test1") { rs -> rs }
        client.invoke<Unit>("request", mapOf("q1" to "test1"))

        Mockito.reset(httpClient)
        onRequest("POST", "http://test-url:8080/test?q1=test1&q2=test2") { rs -> rs }
        client.invoke<Unit>("request", mapOf("q1" to "test1", "q2" to "test2"))
    }
}
