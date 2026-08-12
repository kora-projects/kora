package io.koraframework.http.client.symbol.processor

import io.koraframework.common.Either
import io.koraframework.http.client.common.exception.HttpClientResponseException
import io.koraframework.ksp.common.TestUtils
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.Mockito.reset

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
              fun test(): io.koraframework.common.Either<String, Throwable>
            }
            """.trimIndent(),
            """
            class Test200Mapper : AbstractTestMapper<String, Throwable>("200-string-from-mapper") 
            """.trimIndent(),
            """
            class TestDefaultMapper : AbstractTestMapper<String, Throwable>("default-string-from-mapper") 
            """.trimIndent(),
            """
            abstract class AbstractTestMapper<T, E>(val t: T) : HttpClientResponseMapper<io.koraframework.common.Either<T, E>> {
            
              override fun apply(rs: HttpClientResponse): io.koraframework.common.Either<T, E> {
                  return io.koraframework.common.Either.left(t)
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
              fun test(): io.koraframework.common.Either<String, Throwable>
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
            abstract class AbstractParentTestMapper<T, E, GRO, STATIC>(val t: T) : HttpClientResponseMapper<io.koraframework.common.Either<T, E>> {
            
              override fun apply(rs: HttpClientResponse): io.koraframework.common.Either<T, E> {
                  return io.koraframework.common.Either.left(t)
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
    fun testCodeRange() {
        val client = compile(
            listOf<Any>(), """
            @HttpClient
            interface TestClient {
              @ResponseCodeMapper(code = 200, codeTo = 299, mapper = OkMapper::class)
              @ResponseCodeMapper(code = 400, codeTo = 599, mapper = ErrorMapper::class)
              @HttpRoute(method = "GET", path = "/test")
              fun test(): String
            }
            """.trimIndent(), """
            class OkMapper : HttpClientResponseMapper<String> {
              override fun apply(rs: HttpClientResponse): String {
                  return "ok"
              }
            }
            """.trimIndent(), """
            class ErrorMapper : HttpClientResponseMapper<String> {
              override fun apply(rs: HttpClientResponse): String {
                  return "error"
              }
            }
            """.trimIndent()
        )

        for (code in listOf(200, 204, 299)) {
            reset(httpClient)
            onRequest("GET", "http://test-url:8080/test") { rs -> rs.withCode(code) }
            assertThat(client.invoke<String>("test")).isEqualTo("ok")
        }

        for (code in listOf(400, 500, 599)) {
            reset(httpClient)
            onRequest("GET", "http://test-url:8080/test") { rs -> rs.withCode(code) }
            assertThat(client.invoke<String>("test")).isEqualTo("error")
        }

        reset(httpClient)
        onRequest("GET", "http://test-url:8080/test") { rs -> rs.withCode(300) }
        assertThatThrownBy { client.invoke<String>("test") }.isInstanceOf(HttpClientResponseException::class.java)
    }

    @Test
    fun testCodeRangeWithNestedExactCode() {
        val client = compile(
            listOf<Any>(), """
            @HttpClient
            interface TestClient {
              @ResponseCodeMapper(code = 200, codeTo = 299, mapper = RangeMapper::class)
              @ResponseCodeMapper(code = 201, mapper = ExactMapper::class)
              @HttpRoute(method = "GET", path = "/test")
              fun test(): String
            }
            """.trimIndent(), """
            class RangeMapper : HttpClientResponseMapper<String> {
              override fun apply(rs: HttpClientResponse): String {
                  return "range"
              }
            }
            """.trimIndent(), """
            class ExactMapper : HttpClientResponseMapper<String> {
              override fun apply(rs: HttpClientResponse): String {
                  return "exact"
              }
            }
            """.trimIndent()
        )

        reset(httpClient)
        onRequest("GET", "http://test-url:8080/test") { rs -> rs.withCode(201) }
        assertThat(client.invoke<String>("test")).isEqualTo("exact")

        reset(httpClient)
        onRequest("GET", "http://test-url:8080/test") { rs -> rs.withCode(200) }
        assertThat(client.invoke<String>("test")).isEqualTo("range")

        reset(httpClient)
        onRequest("GET", "http://test-url:8080/test") { rs -> rs.withCode(202) }
        assertThat(client.invoke<String>("test")).isEqualTo("range")
    }

    @Test
    fun testCodeRangeWithDefault() {
        val client = compile(
            listOf<Any>(), """
            @HttpClient
            interface TestClient {
              @ResponseCodeMapper(code = 200, codeTo = 299, mapper = OkMapper::class)
              @ResponseCodeMapper(code = ResponseCodeMapper.DEFAULT, mapper = DefaultMapper::class)
              @HttpRoute(method = "GET", path = "/test")
              fun test(): String
            }
            """.trimIndent(), """
            class OkMapper : HttpClientResponseMapper<String> {
              override fun apply(rs: HttpClientResponse): String {
                  return "ok"
              }
            }
            """.trimIndent(), """
            class DefaultMapper : HttpClientResponseMapper<String> {
              override fun apply(rs: HttpClientResponse): String {
                  return "default"
              }
            }
            """.trimIndent()
        )

        reset(httpClient)
        onRequest("GET", "http://test-url:8080/test") { rs -> rs.withCode(200) }
        assertThat(client.invoke<String>("test")).isEqualTo("ok")

        reset(httpClient)
        onRequest("GET", "http://test-url:8080/test") { rs -> rs.withCode(404) }
        assertThat(client.invoke<String>("test")).isEqualTo("default")
    }

    @Test
    fun testCodeRangeWithExceptionMapper() {
        val client = compile(
            listOf<Any>(), """
            @HttpClient
            interface TestClient {
              @ResponseCodeMapper(code = 200, codeTo = 299, mapper = OkMapper::class)
              @ResponseCodeMapper(code = 400, codeTo = 599, mapper = ExceptionMapper::class)
              @HttpRoute(method = "GET", path = "/test")
              fun test(): String
            }
            """.trimIndent(), """
            class OkMapper : HttpClientResponseMapper<String> {
              override fun apply(rs: HttpClientResponse): String {
                  return "ok"
              }
            }
            """.trimIndent(), """
            class ExceptionMapper : HttpClientResponseMapper<Exception> {
              override fun apply(rs: HttpClientResponse): Exception {
                  return RuntimeException("range-error")
              }
            }
            """.trimIndent()
        )

        reset(httpClient)
        onRequest("GET", "http://test-url:8080/test") { rs -> rs.withCode(500) }
        assertThatThrownBy { client.invoke<String>("test") }
            .isExactlyInstanceOf(RuntimeException::class.java)
            .hasMessage("range-error")
    }

    @Test
    fun testCodeRangeVoid() {
        val client = compile(
            listOf<Any>(), """
            @HttpClient
            interface TestClient {
              @ResponseCodeMapper(code = 200, codeTo = 299, mapper = OkMapper::class)
              @HttpRoute(method = "GET", path = "/test")
              fun test(): Unit
            }
            """.trimIndent(), """
            class OkMapper : HttpClientResponseMapper<Unit> {
              override fun apply(rs: HttpClientResponse) {
              }
            }
            """.trimIndent()
        )

        reset(httpClient)
        onRequest("GET", "http://test-url:8080/test") { rs -> rs.withCode(204) }
        client.invoke<Unit>("test")
    }

    @Test
    fun testCodeRangeInjectedMapper() {
        val client = compile(
            listOf<Any>(newGenerated("RangeMapper")), """
            @HttpClient
            interface TestClient {
              @ResponseCodeMapper(code = 200, codeTo = 299, mapper = RangeMapper::class)
              @HttpRoute(method = "GET", path = "/test")
              fun test(): String
            }
            """.trimIndent(), """
            open class RangeMapper : HttpClientResponseMapper<String> {
              override fun apply(rs: HttpClientResponse): String {
                  return "injected"
              }
            }
            """.trimIndent()
        )

        reset(httpClient)
        onRequest("GET", "http://test-url:8080/test") { rs -> rs.withCode(250) }
        assertThat(client.invoke<String>("test")).isEqualTo("injected")
    }

    @Test
    fun testCodeToLessThanCodeFails() {
        compile0(
            listOf(HttpClientSymbolProcessorProvider()), """
            @HttpClient
            interface TestClient {
              @ResponseCodeMapper(code = 299, codeTo = 200, mapper = TestMapper::class)
              @HttpRoute(method = "GET", path = "/test")
              fun test(): String
            }
            """.trimIndent(), """
            class TestMapper : HttpClientResponseMapper<String> {
              override fun apply(rs: HttpClientResponse): String {
                  return "ok"
              }
            }
            """.trimIndent()
        )
        val failure = compileResult as TestUtils.ProcessingResult.Failure
        assertThat(failure.messages.joinToString("\n")).contains("codeTo")
    }

    @Test
    fun testCodeToWithDefaultCodeFails() {
        compile0(
            listOf(HttpClientSymbolProcessorProvider()), """
            @HttpClient
            interface TestClient {
              @ResponseCodeMapper(code = ResponseCodeMapper.DEFAULT, codeTo = 299, mapper = TestMapper::class)
              @HttpRoute(method = "GET", path = "/test")
              fun test(): String
            }
            """.trimIndent(), """
            class TestMapper : HttpClientResponseMapper<String> {
              override fun apply(rs: HttpClientResponse): String {
                  return "ok"
              }
            }
            """.trimIndent()
        )
        val failure = compileResult as TestUtils.ProcessingResult.Failure
        assertThat(failure.messages.joinToString("\n")).contains("codeTo")
    }

    @Test
    fun testPartialOverlapFails() {
        compile0(
            listOf(HttpClientSymbolProcessorProvider()), """
            @HttpClient
            interface TestClient {
              @ResponseCodeMapper(code = 200, codeTo = 299, mapper = TestMapper::class)
              @ResponseCodeMapper(code = 250, codeTo = 350, mapper = TestMapper::class)
              @HttpRoute(method = "GET", path = "/test")
              fun test(): String
            }
            """.trimIndent(), """
            class TestMapper : HttpClientResponseMapper<String> {
              override fun apply(rs: HttpClientResponse): String {
                  return "ok"
              }
            }
            """.trimIndent()
        )
        val failure = compileResult as TestUtils.ProcessingResult.Failure
        assertThat(failure.messages.joinToString("\n")).contains("partially overlap")
    }

    @Test
    fun testDuplicateExactCodeFails() {
        compile0(
            listOf(HttpClientSymbolProcessorProvider()), """
            @HttpClient
            interface TestClient {
              @ResponseCodeMapper(code = 200, mapper = TestMapper::class)
              @ResponseCodeMapper(code = 200, mapper = TestMapper::class)
              @HttpRoute(method = "GET", path = "/test")
              fun test(): String
            }
            """.trimIndent(), """
            class TestMapper : HttpClientResponseMapper<String> {
              override fun apply(rs: HttpClientResponse): String {
                  return "ok"
              }
            }
            """.trimIndent()
        )
        val failure = compileResult as TestUtils.ProcessingResult.Failure
        assertThat(failure.messages.joinToString("\n")).contains("duplicate mapping")
    }

    @Test
    fun testDuplicateDefaultFails() {
        compile0(
            listOf(HttpClientSymbolProcessorProvider()), """
            @HttpClient
            interface TestClient {
              @ResponseCodeMapper(code = ResponseCodeMapper.DEFAULT, mapper = TestMapper::class)
              @ResponseCodeMapper(code = ResponseCodeMapper.DEFAULT, mapper = TestMapper::class)
              @HttpRoute(method = "GET", path = "/test")
              fun test(): String
            }
            """.trimIndent(), """
            class TestMapper : HttpClientResponseMapper<String> {
              override fun apply(rs: HttpClientResponse): String {
                  return "ok"
              }
            }
            """.trimIndent()
        )
        val failure = compileResult as TestUtils.ProcessingResult.Failure
        assertThat(failure.messages.joinToString("\n")).contains("duplicate DEFAULT")
    }
}
