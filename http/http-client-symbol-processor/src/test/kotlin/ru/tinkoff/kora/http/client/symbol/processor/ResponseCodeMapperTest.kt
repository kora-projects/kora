package ru.tinkoff.kora.http.client.symbol.processor

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.Mockito.reset
import ru.tinkoff.kora.common.util.Either

class ResponseCodeMapperTest : AbstractHttpClientTest() {

    @Test
    fun testGenericResponseMapper() {
        val client = compile(
            listOf<Any>(), """
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
                  return "test-string-from-mapper"
              }
            }
            """.trimIndent(), """
            class NullMapper<T> : HttpClientResponseMapper<T> {
              override fun apply(rs: HttpClientResponse): T? {
                  return null
              }
            }
            """.trimIndent()
        )
        onRequest("POST", "http://test-url:8080/test") { rs -> rs.withCode(200) }
        assertThat(client.invoke<String?>("test")).isEqualTo("test-string-from-mapper")

        reset(httpClient)
        onRequest("POST", "http://test-url:8080/test") { rs -> rs.withCode(404) }
        assertThat(client.invoke<String?>("test")).isNull()
    }

    @Test
    fun testCodeMapperNoParams() {
        val client = compile(
            listOf<Any>(newGenerated("TestMapper")), """
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
            """.trimIndent()
        )
        onRequest("POST", "http://test-url:8080/test") { rs -> rs.withCode(200) }
        client.invoke<String?>("test")

        reset(httpClient)
        onRequest("POST", "http://test-url:8080/test") { rs -> rs.withCode(404) }
        assertThat(client.invoke<String?>("test")).isNull()
    }

    @Test
    fun testCodeMappersByType() {
        val client = compile(
            listOf<Any>(newGenerated("Rs1Mapper"), newGenerated("Rs2Mapper")), """
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
            """.trimIndent()
        )
        onRequest("POST", "http://test-url:8080/test") { rs -> rs.withCode(200) }
        assertThat(client.invoke<Any?>("test")).hasToString("Rs1(code=200)")

        reset(httpClient)
        onRequest("POST", "http://test-url:8080/test") { rs -> rs.withCode(404) }
        assertThat(client.invoke<Any?>("test")).hasToString("Rs2(code=404)")
    }

    @Test
    fun testCodeMappersByTypeWithTag() {
        val client = compile(
            listOf<Any>(newGenerated("Rs1Mapper"), newGenerated("Rs2Mapper")), """
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
            """.trimIndent()
        )
        onRequest("POST", "http://test-url:8080/test") { rs -> rs.withCode(200) }
        assertThat(client.invoke<Any?>("test")).hasToString("Rs1(code=200)")

        reset(httpClient)
        onRequest("POST", "http://test-url:8080/test") { rs -> rs.withCode(404) }
        assertThat(client.invoke<Any?>("test")).hasToString("Rs2(code=404)")
    }

    @Test
    fun testTypeException() {
        val client = compile(
            listOf<Any>(newGenerated("ExceptionMapper")), """
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
            """.trimIndent()
        )
        onRequest("POST", "http://test-url:8080/test") { rs -> rs.withCode(200) }
        client.invoke<String?>("test")

        reset(httpClient)
        onRequest("POST", "http://test-url:8080/test") { rs -> rs.withCode(404) }
        assertThatThrownBy { client.invoke<String?>("test") }
            .isExactlyInstanceOf(RuntimeException::class.java)
            .hasMessage("test")
    }

    @Test
    fun testMapperException() {
        val client = compile(
            listOf<Any>(), """
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
            """.trimIndent()
        )
        onRequest("POST", "http://test-url:8080/test") { rs -> rs.withCode(200) }
        client.invoke<String?>("test")

        reset(httpClient)
        onRequest("POST", "http://test-url:8080/test") { rs -> rs.withCode(404) }
        assertThatThrownBy { client.invoke<String?>("test") }
            .isExactlyInstanceOf(RuntimeException::class.java)
            .hasMessage("test")
    }

    @Test
    fun testInheritedResponseMapper() {
        val client = compile(
            listOf<Any>(), """
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
            """.trimIndent()
        )
        onRequest("POST", "http://test-url:8080/test") { rs -> rs.withCode(200) }
        assertThat(client.invoke<String?>("test")).isEqualTo("test-string-from-mapper")
    }

    @Test
    fun testAbstractGenericResponseMapper() {
        compile(
            listOf(), """
            @HttpClient             
            interface TestClient {
            
              @ResponseCodeMapper(code = 200, mapper = Test200Mapper::class)
              @ResponseCodeMapper(code = ResponseCodeMapper.DEFAULT, mapper = TestDefaultMapper::class)
              @HttpRoute(method = "GET", path = "/test")
              fun test(): ru.tinkoff.kora.common.util.Either<String, Throwable>
            }
            """.trimIndent(),
            """
            class Test200Mapper : AbstractTestMapper<String, Throwable>("200-string-from-mapper") 
            """.trimIndent(),
            """
            class TestDefaultMapper : AbstractTestMapper<String, Throwable>("default-string-from-mapper") 
            """.trimIndent(),
            """
            abstract class AbstractTestMapper<T, E>(val t: T) : HttpClientResponseMapper<ru.tinkoff.kora.common.util.Either<T, E>> {
            
              override fun apply(rs: HttpClientResponse): ru.tinkoff.kora.common.util.Either<T, E> {
                  return ru.tinkoff.kora.common.util.Either.left(t)
              }
            }
            """.trimIndent()
        )

        reset(httpClient)
        onRequest("GET", "http://test-url:8080/test") { rs -> rs.withCode(200) }
        assertThat(client.invoke<Either<String, Throwable>>("test"))
            .isEqualTo(Either.left<String, Throwable>("200-string-from-mapper"))

        reset(httpClient)
        onRequest("GET", "http://test-url:8080/test") { rs -> rs.withCode(201) }
        assertThat(client.invoke<Either<String, Throwable>>("test"))
            .isEqualTo(Either.left<String, Throwable>("default-string-from-mapper"))

        reset(httpClient)
        onRequest("GET", "http://test-url:8080/test") { rs -> rs.withCode(500) }
        assertThat(client.invoke<Either<String, Throwable>>("test"))
            .isEqualTo(Either.left<String, Throwable>("default-string-from-mapper"))
    }

    @Test
    fun testComplexAbstractGenericResponseMapper() {
        compile(
            listOf(), """
            @HttpClient             
            interface TestClient {
            
              @ResponseCodeMapper(code = 200, mapper = Test200Mapper::class)
              @ResponseCodeMapper(code = ResponseCodeMapper.DEFAULT, mapper = TestDefaultMapper::class)
              @HttpRoute(method = "GET", path = "/test")
              fun test(): ru.tinkoff.kora.common.util.Either<String, Throwable>
            }
            """.trimIndent(),
            """
            class Test200Mapper : AbstractChildTestMapper<String, Int, Throwable>("200-string-from-mapper") 
            """.trimIndent(),
            """
            class TestDefaultMapper : AbstractChildTestMapper<String, Long, Throwable>("default-string-from-mapper") 
            """.trimIndent(),
            """
            abstract class AbstractChildTestMapper<K, G, E>(t: K) : AbstractParentTestMapper<K, E, G, Double>(t)
            """.trimIndent(),
            """
            abstract class AbstractParentTestMapper<T, E, GRO, STATIC>(val t: T) : HttpClientResponseMapper<ru.tinkoff.kora.common.util.Either<T, E>> {
            
              override fun apply(rs: HttpClientResponse): ru.tinkoff.kora.common.util.Either<T, E> {
                  return ru.tinkoff.kora.common.util.Either.left(t)
              }
            }
            """.trimIndent()
        )

        reset(httpClient)
        onRequest("GET", "http://test-url:8080/test") { rs -> rs.withCode(200) }
        assertThat(client.invoke<Either<String, Throwable>>("test"))
            .isEqualTo(Either.left<String, Throwable>("200-string-from-mapper"))

        reset(httpClient)
        onRequest("GET", "http://test-url:8080/test") { rs -> rs.withCode(201) }
        assertThat(client.invoke<Either<String, Throwable>>("test"))
            .isEqualTo(Either.left<String, Throwable>("default-string-from-mapper"))

        reset(httpClient)
        onRequest("GET", "http://test-url:8080/test") { rs -> rs.withCode(500) }
        assertThat(client.invoke<Either<String, Throwable>>("test"))
            .isEqualTo(Either.left<String, Throwable>("default-string-from-mapper"))
    }
}
