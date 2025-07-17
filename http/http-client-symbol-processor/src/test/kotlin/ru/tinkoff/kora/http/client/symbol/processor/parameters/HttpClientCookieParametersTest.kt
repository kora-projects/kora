package ru.tinkoff.kora.http.client.symbol.processor.parameters

import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.argThat
import org.mockito.kotlin.verify
import ru.tinkoff.kora.http.client.common.writer.StringParameterConverter
import ru.tinkoff.kora.http.client.symbol.processor.AbstractHttpClientTest
import ru.tinkoff.kora.http.common.cookie.Cookie
import ru.tinkoff.kora.http.common.header.HttpHeaders

class HttpClientCookieParametersTest : AbstractHttpClientTest() {

    @Test
    fun testCookieParam() {
        val client = compile(listOf<Any>(), """
            @HttpClient
            interface TestClient {
              @HttpRoute(method = "POST", path = "/test")
              fun request(@Cookie("some-cookie-param") hParam: String)
            }
            
            """.trimIndent())
        onRequest("POST", "http://test-url:8080/test") { rs -> rs }
        client.invoke<Unit>("request", "test1")
        verify(httpClient).execute(argThat { it -> it.headers().getFirst("Cookie") == "some-cookie-param=test1" })

        Mockito.reset(httpClient)
        onRequest("POST", "http://test-url:8080/test") { rs -> rs }
        client.invoke<Unit>("request", "test2")
        verify(httpClient).execute(argThat { it -> it.headers().getFirst("Cookie") == "some-cookie-param=test2" })
    }

    @Test
    fun testIntParam() {
        val client = compile(listOf<Any>(), """
            @HttpClient
            interface TestClient {
              @HttpRoute(method = "POST", path = "/test")
              fun request(@Cookie("some-cookie-param") hParam: Int)
            }
            
            """.trimIndent())
        onRequest("POST", "http://test-url:8080/test") { rs -> rs }
        client.invoke<Unit>("request", 10)
        verify(httpClient).execute(argThat { it -> it.headers().getFirst("Cookie") == "some-cookie-param=10" })

        Mockito.reset(httpClient)
        onRequest("POST", "http://test-url:8080/test") { rs -> rs }
        client.invoke<Unit>("request", 20)
        verify(httpClient).execute(argThat { it -> it.headers().getFirst("Cookie") == "some-cookie-param=20" })
    }

    @Test
    fun testListCookieParam() {
        val client = compile(listOf<Any>(), """
            @HttpClient
            interface TestClient {
              @HttpRoute(method = "POST", path = "/test")
              fun request(@Cookie("some-cookie-param") hParam: List<String>)
            }
            
            """.trimIndent())
        onRequest("POST", "http://test-url:8080/test") { rs -> rs }
        client.invoke<Unit>("request", listOf("test1"))
        verify(httpClient).execute(argThat { it -> it.headers().getFirst("Cookie") == "some-cookie-param=test1" })

        Mockito.reset(httpClient)
        onRequest("POST", "http://test-url:8080/test") { rs -> rs }
        client.invoke<Unit>("request", listOf("test1", "test2"))
        verify(httpClient).execute(argThat { it -> it.headers().getAll("Cookie") == listOf("some-cookie-param=test1", "some-cookie-param=test2") })
    }

    @Test
    fun testIntListCookieParam() {
        val client = compile(listOf<Any>(), """
            @HttpClient
            interface TestClient {
              @HttpRoute(method = "POST", path = "/test")
              fun request(@Cookie("some-cookie-param") hParam: List<Int>)
            }
            
            """.trimIndent())
        onRequest("POST", "http://test-url:8080/test") { rs -> rs }
        client.invoke<Unit>("request", listOf(10))
        verify(httpClient).execute(argThat { it -> it.headers().getFirst("Cookie") == "some-cookie-param=10" })

        Mockito.reset(httpClient)
        onRequest("POST", "http://test-url:8080/test") { rs -> rs }
        client.invoke<Unit>("request", listOf(10, 20))
        verify(httpClient).execute(argThat { it -> it.headers().getAll("Cookie") == listOf("some-cookie-param=10", "some-cookie-param=20") })
    }

    @Test
    fun testSetCookieParam() {
        val client = compile(listOf<Any>(), """
            @HttpClient
            interface TestClient {
              @HttpRoute(method = "POST", path = "/test")
              fun request(@Cookie("some-cookie-param") hParam: Set<String>)
            }
            
            """.trimIndent())
        onRequest("POST", "http://test-url:8080/test") { rs -> rs }
        client.invoke<Unit>("request", linkedSetOf("test1"))
        verify(httpClient).execute(argThat { it -> it.headers().getFirst("Cookie") == "some-cookie-param=test1" })

        Mockito.reset(httpClient)
        onRequest("POST", "http://test-url:8080/test") { rs -> rs }
        client.invoke<Unit>("request", linkedSetOf("test10", "test20"))
        verify(httpClient).execute(argThat { it -> it.headers().getAll("Cookie") == listOf("some-cookie-param=test10", "some-cookie-param=test20") })
    }

    @Test
    fun testMapCookieParam() {
        val client = compile(listOf<Any>(), """
            @HttpClient
            interface TestClient {
              @HttpRoute(method = "POST", path = "/test")
              fun request(@Cookie params: Map<String, String>)
            }
            """.trimIndent())

        onRequest("POST", "http://test-url:8080/test") { rs -> rs }
        client.invoke<Unit>("request", mapOf("c1" to "test1"))
        verify(httpClient).execute(argThat { it -> it.headers().getFirst("Cookie") == "c1=test1" })

        Mockito.reset(httpClient)
        onRequest("POST", "http://test-url:8080/test") { rs -> rs }
        client.invoke<Unit>("request", mapOf("c1" to "test1", "c2" to "test2"))
        verify(httpClient).execute(argThat { it -> setOf(it.headers().getAll("Cookie")) == setOf("c1=test1", "c2=test2") })
    }

    @Test
    fun testMapCookieParamWithConverter() {
        val client = compile(listOf<Any>(StringParameterConverter<Any> { it.toString() }), """
            @HttpClient
            interface TestClient {
              @HttpRoute(method = "POST", path = "/test")
              fun request(@Cookie params: Map<String, Any>)
            }
            """.trimIndent())

        onRequest("POST", "http://test-url:8080/test") { rs -> rs }
        client.invoke<Unit>("request", mapOf("c1" to "test1"))
        verify(httpClient).execute(argThat { it -> it.headers().getFirst("Cookie") == "c1=test1" })

        Mockito.reset(httpClient)
        onRequest("POST", "http://test-url:8080/test") { rs -> rs }
        client.invoke<Unit>("request", mapOf("c1" to "test1", "c2" to "test2"))
        verify(httpClient).execute(argThat { it -> setOf(it.headers().getAll("Cookie")) == setOf("c1=test1", "c2=test2") })
    }

    @Test
    fun testMapCookieParamNullable() {
        val client = compile(listOf<Any>(), """
            @HttpClient
            interface TestClient {
              @HttpRoute(method = "POST", path = "/test")
              fun request(@Cookie params: Map<String, String?>)
            }
            """.trimIndent())

        onRequest("POST", "http://test-url:8080/test") { rs -> rs }
        client.invoke<Unit>("request", mapOf("c1" to "test1"))
        verify(httpClient).execute(argThat { it -> it.headers().getFirst("Cookie") == "c1=test1" })

        Mockito.reset(httpClient)
        onRequest("POST", "http://test-url:8080/test") { rs -> rs }
        client.invoke<Unit>("request", mapOf("c1" to "test1", "c2" to "test2"))
        verify(httpClient).execute(argThat { it -> setOf(it.headers().getAll("Cookie")) == setOf("c1=test1", "c2=test2") })
    }

    @Test
    fun testMapCookieParamWithConverterNullable() {
        val client = compile(listOf<Any>(StringParameterConverter<Any> { it.toString() }), """
            @HttpClient
            interface TestClient {
              @HttpRoute(method = "POST", path = "/test")
              fun request(@Cookie params: Map<String, Any?>)
            }
            """.trimIndent())

        onRequest("POST", "http://test-url:8080/test") { rs -> rs }
        client.invoke<Unit>("request", mapOf("c1" to "test1"))
        verify(httpClient).execute(argThat { it -> it.headers().getFirst("Cookie") == "c1=test1" })

        Mockito.reset(httpClient)
        onRequest("POST", "http://test-url:8080/test") { rs -> rs }
        client.invoke<Unit>("request", mapOf("c1" to "test1", "c2" to "test2"))
        verify(httpClient).execute(argThat { it -> setOf(it.headers().getAll("Cookie")) == setOf("c1=test1", "c2=test2") })
    }

    @Test
    fun testHttpCookieParam() {
        val client = compile(listOf<Any>(), """
            @HttpClient
            interface TestClient {
              @HttpRoute(method = "POST", path = "/test")
              fun request(@Cookie params: ru.tinkoff.kora.http.common.cookie.Cookie)
            }
            """.trimIndent())

        onRequest("POST", "http://test-url:8080/test") { rs -> rs }
        client.invoke<Unit>("request", Cookie.of("c1", "test1"))
        verify(httpClient).execute(argThat { it -> it.headers().getFirst("Cookie") == "c1=test1" })

        Mockito.reset(httpClient)
        onRequest("POST", "http://test-url:8080/test") { rs -> rs }
        client.invoke<Unit>("request", HttpHeaders.of("c1", "test1", "c2", "test2"))
        verify(httpClient).execute(argThat { it -> setOf(it.headers().getAll("Cookie")) == setOf("c1=test1", "c2=test2") })
    }
}
