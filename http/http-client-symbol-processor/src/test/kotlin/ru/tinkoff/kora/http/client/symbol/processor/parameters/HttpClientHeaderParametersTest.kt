package ru.tinkoff.kora.http.client.symbol.processor.parameters

import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.argThat
import org.mockito.kotlin.verify
import ru.tinkoff.kora.http.client.symbol.processor.AbstractHttpClientTest

class HttpClientHeaderParametersTest : AbstractHttpClientTest() {
    @Test
    fun testQueryParam() {
        val client = compile(listOf<Any>(), """
            @HttpClient
            interface TestClient {
              @HttpRoute(method = "POST", path = "/test")
              fun request(@Header("some-header-param") hParam: String)
            }
            
            """.trimIndent())
        onRequest("POST", "http://test-url:8080/test") { rs -> rs }
        client.invoke<Unit>("request", "test1")
        verify(httpClient).execute(argThat { it -> it.headers().getFirst("some-header-param") == "test1" })

        Mockito.reset(httpClient)
        onRequest("POST", "http://test-url:8080/test") { rs -> rs }
        client.invoke<Unit>("request", "test2")
        verify(httpClient).execute(argThat { it -> it.headers().getFirst("some-header-param") == "test2" })
    }

    @Test
    fun testIntParam() {
        val client = compile(listOf<Any>(), """
            @HttpClient
            interface TestClient {
              @HttpRoute(method = "POST", path = "/test")
              fun request(@Header("some-header-param") hParam: Int)
            }
            
            """.trimIndent())
        onRequest("POST", "http://test-url:8080/test") { rs -> rs }
        client.invoke<Unit>("request", 10)
        verify(httpClient).execute(argThat { it -> it.headers().getFirst("some-header-param") == "10" })

        Mockito.reset(httpClient)
        onRequest("POST", "http://test-url:8080/test") { rs -> rs }
        client.invoke<Unit>("request", 20)
        verify(httpClient).execute(argThat { it -> it.headers().getFirst("some-header-param") == "20" })
    }

    @Test
    fun testListQueryParam() {
        val client = compile(listOf<Any>(), """
            @HttpClient
            interface TestClient {
              @HttpRoute(method = "POST", path = "/test")
              fun request(@Header("some-header-param") hParam: List<String>)
            }
            
            """.trimIndent())
        onRequest("POST", "http://test-url:8080/test") { rs -> rs }
        client.invoke<Unit>("request", listOf("test1"))
        verify(httpClient).execute(argThat { it -> it.headers().getFirst("some-header-param") == "test1" })

        Mockito.reset(httpClient)
        onRequest("POST", "http://test-url:8080/test") { rs -> rs }
        client.invoke<Unit>("request", listOf("test1", "test2"))
        verify(httpClient).execute(argThat { it -> it.headers().getAll("some-header-param") == listOf("test1", "test2") })
    }

    @Test
    fun testIntListQueryParam() {
        val client = compile(listOf<Any>(), """
            @HttpClient
            interface TestClient {
              @HttpRoute(method = "POST", path = "/test")
              fun request(@Header("some-header-param") hParam: List<Int>)
            }
            
            """.trimIndent())
        onRequest("POST", "http://test-url:8080/test") { rs -> rs }
        client.invoke<Unit>("request", listOf(10))
        verify(httpClient).execute(argThat { it -> it.headers().getFirst("some-header-param") == "10" })

        Mockito.reset(httpClient)
        onRequest("POST", "http://test-url:8080/test") { rs -> rs }
        client.invoke<Unit>("request", listOf(10, 20))
        verify(httpClient).execute(argThat { it -> it.headers().getAll("some-header-param") == listOf("10", "20") })
    }

    @Test
    fun testSetQueryParam() {
        val client = compile(listOf<Any>(), """
            @HttpClient
            interface TestClient {
              @HttpRoute(method = "POST", path = "/test")
              fun request(@Header("some-header-param") hParam: Set<String>)
            }
            
            """.trimIndent())
        onRequest("POST", "http://test-url:8080/test") { rs -> rs }
        client.invoke<Unit>("request", linkedSetOf("test1"))
        verify(httpClient).execute(argThat { it -> it.headers().getFirst("some-header-param") == "test1" })

        Mockito.reset(httpClient)
        onRequest("POST", "http://test-url:8080/test") { rs -> rs }
        client.invoke<Unit>("request", linkedSetOf("test10", "test20"))
        verify(httpClient).execute(argThat { it -> it.headers().getAll("some-header-param") == listOf("test10", "test20") })
    }

}
