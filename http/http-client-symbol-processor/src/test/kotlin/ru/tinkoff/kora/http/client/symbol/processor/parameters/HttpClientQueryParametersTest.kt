package ru.tinkoff.kora.http.client.symbol.processor.parameters

import org.junit.jupiter.api.Test
import org.mockito.Mockito
import ru.tinkoff.kora.http.client.symbol.processor.AbstractHttpClientTest

class HttpClientQueryParametersTest : AbstractHttpClientTest() {
    @Test
    fun testQueryParam() {
        val client = compile(listOf<Any>(), """
            @HttpClient
            interface TestClient {
              @HttpRoute(method = "POST", path = "/test")
              fun request(@Query qParam: String)
            }
            
            """.trimIndent())
        onRequest("POST", "http://test-url:8080/test?qParam=test1") { rs -> rs }
        client.invoke<Unit>("request", "test1")

        Mockito.reset(httpClient)
        onRequest("POST", "http://test-url:8080/test?qParam=test2") { rs -> rs }
        client.invoke<Unit>("request", "test2")
    }

    @Test
    fun testIntParam() {
        val client = compile(listOf<Any>(), """
            @HttpClient
            interface TestClient {
              @HttpRoute(method = "POST", path = "/test")
              fun request(@Query qParam: Int)
            }
            
            """.trimIndent())
        onRequest("POST", "http://test-url:8080/test?qParam=10") { rs -> rs }
        client.invoke<Unit>("request", 10)

        Mockito.reset(httpClient)
        onRequest("POST", "http://test-url:8080/test?qParam=20") { rs -> rs }
        client.invoke<Unit>("request", 20)
    }

    @Test
    fun testNullableIntParam() {
        val client = compile(listOf<Any>(), """
            @HttpClient
            interface TestClient {
              @HttpRoute(method = "POST", path = "/test")
              fun request(@Query qParam: Int?)
            }
            
            """.trimIndent())
        onRequest("POST", "http://test-url:8080/test?qParam=10") { rs -> rs }
        client.invoke<Unit>("request", 10)

        Mockito.reset(httpClient)
        onRequest("POST", "http://test-url:8080/test") { rs -> rs }
        client.invoke<Unit>("request", null)
    }

    @Test
    fun testListQueryParam() {
        val client = compile(listOf<Any>(), """
            @HttpClient
            interface TestClient {
              @HttpRoute(method = "POST", path = "/test")
              fun request(@Query qParam: List<String>)
            }
            
            """.trimIndent())
        onRequest("POST", "http://test-url:8080/test?qParam=test1") { rs -> rs }
        client.invoke<Unit>("request", listOf("test1"))

        Mockito.reset(httpClient)
        onRequest("POST", "http://test-url:8080/test?qParam=test1&qParam=test2") { rs -> rs }
        client.invoke<Unit>("request", listOf("test1", "test2"))
    }

    @Test
    fun testIntListQueryParam() {
        val client = compile(listOf<Any>(), """
            @HttpClient
            interface TestClient {
              @HttpRoute(method = "POST", path = "/test")
              fun request(@Query qParam: List<Int>)
            }
            
            """.trimIndent())
        onRequest("POST", "http://test-url:8080/test?qParam=10") { rs -> rs }
        client.invoke<Unit>("request", listOf(10))

        Mockito.reset(httpClient)
        onRequest("POST", "http://test-url:8080/test?qParam=10&qParam=20") { rs -> rs }
        client.invoke<Unit>("request", listOf(10, 20))
    }
    @Test
    fun testSetQueryParam() {
        val client = compile(listOf<Any>(), """
            @HttpClient
            interface TestClient {
              @HttpRoute(method = "POST", path = "/test")
              fun request(@Query qParam: Set<String>)
            }
            
            """.trimIndent())
        onRequest("POST", "http://test-url:8080/test?qParam=test1") { rs -> rs }
        client.invoke<Unit>("request", linkedSetOf("test1"))

        Mockito.reset(httpClient)
        onRequest("POST", "http://test-url:8080/test?qParam=test10&qParam=test20") { rs -> rs }
        client.invoke<Unit>("request", linkedSetOf("test10", "test20"))
    }

}
