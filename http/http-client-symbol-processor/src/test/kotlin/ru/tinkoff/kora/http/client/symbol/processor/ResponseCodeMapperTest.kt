package ru.tinkoff.kora.http.client.symbol.processor

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.lang.RuntimeException

class ResponseCodeMapperTest : AbstractHttpClientTest() {
    @Test
    fun testGenericResponseMapper() {
        val client = compile(listOf<Any>(), """
            @HttpClient
            interface TestClient {
              @ResponseCodeMapper(code = 200, mapper = TestMapper::class)
              @ResponseCodeMapper(code = 404, mapper = NullMapper::class)
              @HttpRoute(method = "POST", path = "/test")
              fun test(): String?
            }
            """.trimIndent(), """
            class TestMapper : HttpClientResponseMapper<String> {
              override fun apply(rs: HttpClientResponse): String {
                  return "test-string-from-mapper";
              }
            }
            """.trimIndent(), """
            class NullMapper <T> : HttpClientResponseMapper<T> {
              override fun apply(rs: HttpClientResponse): T? {
                  return null;
              }
            }
            """.trimIndent())
        onRequest("POST", "http://test-url:8080/test") { rs -> rs.withCode(200) }
        client.invoke<String?>("test")

        Mockito.reset(httpClient)
        onRequest("POST", "http://test-url:8080/test") { rs -> rs.withCode(404) }
        assertThat(client.invoke<String?>("test")).isNull()
    }

    @Test
    fun testTypeException() {
        val client = compile(listOf<Any>(newGenerated("ExceptionMapper")), """
            @HttpClient
            interface TestClient {
              @ResponseCodeMapper(code = 200, mapper = TestMapper::class)
              @ResponseCodeMapper(code = 404, type = Exception::class)
              @HttpRoute(method = "POST", path = "/test")
              fun test(): String?
            }
            """.trimIndent(), """
            class TestMapper : HttpClientResponseMapper<String> {
              override fun apply(rs: HttpClientResponse): String {
                  return "test-string-from-mapper";
              }
            }
            """.trimIndent(), """
            class ExceptionMapper : HttpClientResponseMapper<Exception> {
              override fun apply(rs: HttpClientResponse): Exception {
                  return RuntimeException("test");
              }
            }
            """.trimIndent())
        onRequest("POST", "http://test-url:8080/test") { rs -> rs.withCode(200) }
        client.invoke<String?>("test")

        Mockito.reset(httpClient)
        onRequest("POST", "http://test-url:8080/test") { rs -> rs.withCode(404) }
        assertThatThrownBy { client.invoke<String?>("test") }
            .isExactlyInstanceOf(RuntimeException::class.java)
            .hasMessage("test")
    }

    @Test
    fun testMapperException() {
        val client = compile(listOf<Any>(), """
            @HttpClient
            interface TestClient {
              @ResponseCodeMapper(code = 200, mapper = TestMapper::class)
              @ResponseCodeMapper(code = 404, mapper = ExceptionMapper::class)
              @HttpRoute(method = "POST", path = "/test")
              fun test(): String?
            }
            """.trimIndent(), """
            class TestMapper : HttpClientResponseMapper<String> {
              override fun apply(rs: HttpClientResponse): String {
                  return "test-string-from-mapper"
              }
            }
            """.trimIndent(), """
            class ExceptionMapper : HttpClientResponseMapper<Exception> {
              override fun apply(rs: HttpClientResponse): Exception {
                return RuntimeException("test")
              }
            }
            """.trimIndent())
        onRequest("POST", "http://test-url:8080/test") { rs -> rs.withCode(200) }
        client.invoke<String?>("test")

        Mockito.reset(httpClient)
        onRequest("POST", "http://test-url:8080/test") { rs -> rs.withCode(404) }
        assertThatThrownBy { client.invoke<String?>("test") }
            .isExactlyInstanceOf(RuntimeException::class.java)
            .hasMessage("test")
    }
}
