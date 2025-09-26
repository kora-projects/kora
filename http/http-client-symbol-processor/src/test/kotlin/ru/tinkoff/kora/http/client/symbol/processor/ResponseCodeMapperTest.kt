package ru.tinkoff.kora.http.client.symbol.processor

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.Mockito

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
        assertThat(client.invoke<String?>("test")).isEqualTo("test-string-from-mapper")

        Mockito.reset(httpClient)
        onRequest("POST", "http://test-url:8080/test") { rs -> rs.withCode(404) }
        assertThat(client.invoke<String?>("test")).isNull()
    }

    @Test
    fun testCodeMapperNoParams() {
        val client = compile(listOf<Any>(newGenerated("TestMapper")), """
            @HttpClient
            interface TestClient {
              @Tag(TestClient::class)
              @ResponseCodeMapper(code = 200)
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
    fun testCodeMappersByType() {
        val client = compile(listOf<Any>(newGenerated("Rs1Mapper"), newGenerated("Rs2Mapper")), """
            @HttpClient
            interface TestClient {
              sealed interface TestResponse {
                data class Rs1(val code: Int) : TestResponse
                data class Rs2(val code: Int) : TestResponse
              }
            
              @ResponseCodeMapper(code = 200, type = TestClient.TestResponse.Rs1::class)
              @ResponseCodeMapper(code = 404, type = TestClient.TestResponse.Rs2::class)
              @HttpRoute(method = "POST", path = "/test")
              fun test(): TestResponse
            }
            """.trimIndent(), """
            class Rs1Mapper : HttpClientResponseMapper<TestClient.TestResponse.Rs1> {
              override fun apply(rs: HttpClientResponse): TestClient.TestResponse.Rs1 {
                  return TestClient.TestResponse.Rs1(rs.code());
              }
            }
            """.trimIndent(), """
            class Rs2Mapper : HttpClientResponseMapper<TestClient.TestResponse.Rs2> {
              override fun apply(rs: HttpClientResponse): TestClient.TestResponse.Rs2 {
                  return TestClient.TestResponse.Rs2(rs.code());
              }
            }
            """.trimIndent())
        onRequest("POST", "http://test-url:8080/test") { rs -> rs.withCode(200) }
        assertThat(client.invoke<Any?>("test")).hasToString("Rs1(code=200)")

        Mockito.reset(httpClient)
        onRequest("POST", "http://test-url:8080/test") { rs -> rs.withCode(404) }
        assertThat(client.invoke<Any?>("test")).hasToString("Rs2(code=404)")
    }

    @Test
    fun testCodeMappersByTypeWithTag() {
        val client = compile(listOf<Any>(newGenerated("Rs1Mapper"), newGenerated("Rs2Mapper")), """
            @HttpClient
            interface TestClient {
              sealed interface TestResponse {
                data class Rs1(val code: Int) : TestResponse
                data class Rs2(val code: Int) : TestResponse
              }
            
              @Tag(TestClient::class)
              @ResponseCodeMapper(code = 200, type = TestClient.TestResponse.Rs1::class)
              @ResponseCodeMapper(code = 404, type = TestClient.TestResponse.Rs2::class)
              @HttpRoute(method = "POST", path = "/test")
              fun test(): TestResponse
            }
            """.trimIndent(), """
            class Rs1Mapper : HttpClientResponseMapper<TestClient.TestResponse.Rs1> {
              override fun apply(rs: HttpClientResponse): TestClient.TestResponse.Rs1 {
                  return TestClient.TestResponse.Rs1(rs.code());
              }
            }
            """.trimIndent(), """
            class Rs2Mapper : HttpClientResponseMapper<TestClient.TestResponse.Rs2> {
              override fun apply(rs: HttpClientResponse): TestClient.TestResponse.Rs2 {
                  return TestClient.TestResponse.Rs2(rs.code());
              }
            }
            """.trimIndent())
        onRequest("POST", "http://test-url:8080/test") { rs -> rs.withCode(200) }
        assertThat(client.invoke<Any?>("test")).hasToString("Rs1(code=200)")

        Mockito.reset(httpClient)
        onRequest("POST", "http://test-url:8080/test") { rs -> rs.withCode(404) }
        assertThat(client.invoke<Any?>("test")).hasToString("Rs2(code=404)")
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

    @Test
    fun testInheritedResponseMapper() {
        val client = compile(listOf<Any>(), """
            @HttpClient
            interface TestClient {
              @ResponseCodeMapper(code = 200, mapper = TestMapper::class)
              @HttpRoute(method = "POST", path = "/test")
              fun test(): String?
            }
            """.trimIndent(), """
            class TestMapper : SuperMapper()
            """.trimIndent(), """
            abstract class SuperMapper : HttpClientResponseMapper<String> {
              override fun apply(rs: HttpClientResponse): String {
                  return "test-string-from-mapper"
              }
            }
            """.trimIndent())
        onRequest("POST", "http://test-url:8080/test") { rs -> rs.withCode(200) }
        assertThat(client.invoke<String?>("test")).isEqualTo("test-string-from-mapper")
    }
}
