package ru.tinkoff.kora.http.client.symbol.processor

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import org.mockito.Mockito.reset
import org.mockito.kotlin.whenever
import ru.tinkoff.kora.common.Component
import ru.tinkoff.kora.common.Context
import ru.tinkoff.kora.common.Tag
import ru.tinkoff.kora.http.client.common.HttpClientEncoderException
import ru.tinkoff.kora.http.client.common.HttpClientResponseException
import ru.tinkoff.kora.http.client.common.request.HttpClientRequestMapper
import ru.tinkoff.kora.http.client.common.response.HttpClientResponseMapper
import ru.tinkoff.kora.http.common.body.HttpBody
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.full.primaryConstructor

class BlockingApiTest : AbstractHttpClientTest() {

    @Test
    fun testComponentAnnotationPreserved() {
        val client = compile(
            listOf<Any>(), """
            @Component
            @HttpClient
            interface TestClient {
              @HttpRoute(method = "POST", path = "/test")
              fun request()
            }
            
            """.trimIndent()
        )

        Assertions.assertThat(client.objectClass.annotations.any { a -> a is Component }).isTrue

        onRequest("POST", "http://test-url:8080/test") { rs -> rs.withCode(200) }
        client.invoke<Unit>("request")
    }

    @Test
    fun testBlockingVoid() {
        val client = compile(
            listOf<Any>(), """
            @HttpClient
            interface TestClient {
              @HttpRoute(method = "POST", path = "/test")
              fun request()
            }
            
            """.trimIndent()
        )
        onRequest("POST", "http://test-url:8080/test") { rs -> rs.withCode(200) }
        client.invoke<Unit>("request")

        reset(httpClient)
        onRequest("POST", "http://test-url:8080/test") { rs -> rs.withCode(201) }
        client.invoke<Unit>("request")

        reset(httpClient)
        onRequest("POST", "http://test-url:8080/test") { rs -> rs.withCode(500) }
        Assertions.assertThatThrownBy { client.invoke<Unit>("request") }.isInstanceOf(HttpClientResponseException::class.java)
    }

    @Test
    fun testBlockingNonVoid() {
        val mapper = Mockito.mock(HttpClientResponseMapper::class.java)
        compile(
            listOf(mapper), """
            @HttpClient
            interface TestClient {
              @HttpRoute(method = "POST", path = "/test")
              fun request(): String
            }
            
            """.trimIndent()
        )

        reset(httpClient, mapper)

        whenever(mapper.apply(ArgumentMatchers.any())).thenReturn("test")
        onRequest("POST", "http://test-url:8080/test") { rs -> rs.withCode(200) }
        Assertions.assertThat(client.invoke<String>("request"))
            .isEqualTo("test")

        reset(httpClient, mapper)

        whenever(mapper.apply(ArgumentMatchers.any())).thenReturn("test")
        onRequest("POST", "http://test-url:8080/test") { rs -> rs.withCode(201) }
        Assertions.assertThat(client.invoke<String>("request"))
            .isEqualTo("test")

        reset(httpClient, mapper)

        onRequest("POST", "http://test-url:8080/test") { rs -> rs.withCode(500) }
        Assertions.assertThatThrownBy { client.invoke<String>("request") }.isInstanceOf(HttpClientResponseException::class.java)
        Mockito.verify(mapper, Mockito.never()).apply(ArgumentMatchers.any())
    }

    @Test
    fun testBlockingCustomFinalMapper() {
        compile(
            listOf<Any>(), """
            @HttpClient
            interface TestClient {
              @Mapping(TestMapper::class)
              @HttpRoute(method = "GET", path = "/test")
              fun request(): String
            }
            """.trimIndent(), """
            class TestMapper: HttpClientResponseMapper<String> {
              override fun apply(rs: HttpClientResponse): String {
                  return "test-string-from-mapper";
              }
            }
            """.trimIndent()
        )

        reset(httpClient)
        onRequest("GET", "http://test-url:8080/test") { rs -> rs.withCode(200) }
        Assertions.assertThat(client.invoke<String>("request"))
            .isEqualTo("test-string-from-mapper")

        reset(httpClient)
        onRequest("GET", "http://test-url:8080/test") { rs -> rs.withCode(500) }
        Assertions.assertThat(client.invoke<String>("request"))
            .isEqualTo("test-string-from-mapper")
    }

    @Test
    fun testBlockingCustomMapper() {
        compile(
            listOf(newGenerated("TestMapper")), """
            @HttpClient
            interface TestClient {
              @Mapping(TestMapper::class)
              @HttpRoute(method = "GET", path = "/test")
              fun request(): String
            }
            
            """.trimIndent(), """
            open class TestMapper : HttpClientResponseMapper<String> {
              override fun apply(rs: HttpClientResponse): String {
                  return "test-string-from-mapper";
              }
            }
            
            """.trimIndent()
        )

        reset(httpClient)
        onRequest("GET", "http://test-url:8080/test") { rs -> rs.withCode(200) }
        Assertions.assertThat(client.invoke<String>("request"))
            .isEqualTo("test-string-from-mapper")

        reset(httpClient)
        onRequest("GET", "http://test-url:8080/test") { rs -> rs.withCode(500) }
        Assertions.assertThat(client.invoke<String>("request"))
            .isEqualTo("test-string-from-mapper")
    }

    @Test
    fun testBlockingCustomMapperByTag() {
        compile(
            listOf(newGenerated("TestMapper")), """
            @HttpClient
            interface TestClient {
              @Tag(TestMapper::class)
              @HttpRoute(method = "GET", path = "/test")
              fun request(): String
            }
            
            """.trimIndent(), """
            class TestMapper : HttpClientResponseMapper<String> {
              override fun apply(rs: HttpClientResponse): String {
                  return "test-string-from-mapper";
              }
            }
            
            """.trimIndent()
        )
        Assertions.assertThat(client.objectClass.primaryConstructor!!.parameters[3].hasAnnotation<Tag>())

        reset(httpClient)
        onRequest("GET", "http://test-url:8080/test") { rs -> rs.withCode(200) }
        Assertions.assertThat(client.invoke<String>("request"))
            .isEqualTo("test-string-from-mapper")

        reset(httpClient)
        onRequest("GET", "http://test-url:8080/test") { rs -> rs.withCode(500) }
        Assertions.assertThatThrownBy { client.invoke<String>("request") }
            .isInstanceOf(HttpClientResponseException::class.java)
    }

    @Test
    fun testBlockingFinalCodeMapper() {
        compile(
            listOf<Any>(), """
            @HttpClient
            interface TestClient {
              @ResponseCodeMapper(code = 201, mapper = TestMapper::class)
              @HttpRoute(method = "GET", path = "/test")
              fun test(): String
            }
            """.trimIndent(), """
            class TestMapper : HttpClientResponseMapper<String> {
              override fun apply(rs: HttpClientResponse): String  {
                  return "test-string-from-mapper";
              }
            }
            """.trimIndent()
        )

        reset(httpClient)
        onRequest("GET", "http://test-url:8080/test") { rs -> rs.withCode(201) }
        Assertions.assertThat(client.invoke<String>("test"))
            .isEqualTo("test-string-from-mapper")

        reset(httpClient)
        onRequest("GET", "http://test-url:8080/test") { rs -> rs.withCode(200) }
        Assertions.assertThatThrownBy { client.invoke<String>("test") }.isInstanceOf(HttpClientResponseException::class.java)

        reset(httpClient)
        onRequest("GET", "http://test-url:8080/test") { rs -> rs.withCode(500) }
        Assertions.assertThatThrownBy { client.invoke<String>("test") }.isInstanceOf(HttpClientResponseException::class.java)
    }

    @Test
    fun testBlockingCodeMapper() {
        compile(
            listOf(newGenerated("TestMapper")), """
            @HttpClient
            public interface TestClient {
              @ResponseCodeMapper(code = 201, mapper = TestMapper::class)
              @HttpRoute(method = "GET", path = "/test")
              fun test(): String
            }
            
            """.trimIndent(), """
            open class TestMapper : HttpClientResponseMapper<String> {
              override fun apply(rs: HttpClientResponse): String {
                  return "test-string-from-mapper";
              }
            }
            
            """.trimIndent()
        )

        reset(httpClient)
        onRequest("GET", "http://test-url:8080/test") { rs -> rs.withCode(201) }
        Assertions.assertThat(client.invoke<String>("test"))
            .isEqualTo("test-string-from-mapper")

        reset(httpClient)
        onRequest("GET", "http://test-url:8080/test") { rs -> rs.withCode(200) }
        Assertions.assertThatThrownBy { client.invoke<String>("test") }.isInstanceOf(HttpClientResponseException::class.java)

        reset(httpClient)
        onRequest("GET", "http://test-url:8080/test") { rs -> rs.withCode(500) }
        Assertions.assertThatThrownBy { client.invoke<String>("test") }.isInstanceOf(HttpClientResponseException::class.java)
    }

    @Test
    fun testCodeMapperByType() {
        compile(
            listOf(newGenerated("Test200Mapper"), newGenerated("Test500Mapper")), """
            @HttpClient
            public interface TestClient {
              @ResponseCodeMapper(code = 200, type = TestResponse.Rs200::class)
              @ResponseCodeMapper(code = 500, type = TestResponse.Rs500::class)
              @HttpRoute(method = "GET", path = "/test")
              fun test(): TestResponse
            }
            
            """.trimIndent(), """
            open class Test200Mapper : HttpClientResponseMapper<TestResponse.Rs200> {
              override fun apply(rs: HttpClientResponse) = TestResponse.Rs200()
            }
            """.trimIndent(), """
            open class Test500Mapper : HttpClientResponseMapper<TestResponse.Rs500> {
              override fun apply(rs: HttpClientResponse) = TestResponse.Rs500()
            }
            """.trimIndent(), """
            sealed interface TestResponse {
              class Rs200(): TestResponse { override fun toString() = "Rs200" }
              class Rs500(): TestResponse { override fun toString() = "Rs500" }
            }
            
            """.trimIndent()
        )

        reset(httpClient)
        onRequest("GET", "http://test-url:8080/test") { rs -> rs.withCode(500) }
        var result = client.invoke<Any>("test")
        Assertions.assertThat(result).hasToString("Rs500")

        reset(httpClient)
        onRequest("GET", "http://test-url:8080/test") { rs -> rs.withCode(200) }
        result = client.invoke<Any>("test")
        Assertions.assertThat(result).hasToString("Rs200")

        reset(httpClient)
        onRequest("GET", "http://test-url:8080/test") { rs -> rs.withCode(201) }
        Assertions.assertThatThrownBy { client.invoke<Any>("test") }.isInstanceOf(HttpClientResponseException::class.java)
    }

    @Test
    fun testCodeMapperNoType() {
        compile(
            listOf(newGenerated("TestMapper"), newGenerated("TestMapper")), """
            @HttpClient
            interface TestClient {
              @ResponseCodeMapper(code = 200)
              @ResponseCodeMapper(code = 500)
              @HttpRoute(method = "GET", path = "/test")
              fun test(): TestResponse
            }
            
            """.trimIndent(), """
            open class TestMapper : HttpClientResponseMapper<TestResponse> {
              override fun apply(rs: HttpClientResponse) = if (rs.code() == 200) TestResponse.Rs200() else TestResponse.Rs500()
            }
            """.trimIndent(), """
            sealed interface TestResponse {
              class Rs200(): TestResponse { override fun toString() = "Rs200" }
              class Rs500(): TestResponse { override fun toString() = "Rs500" }
            }
            
            """.trimIndent()
        )

        reset(httpClient)
        onRequest("GET", "http://test-url:8080/test") { rs -> rs.withCode(500) }
        var result = client.invoke<Any>("test")
        Assertions.assertThat(result).hasToString("Rs500")

        reset(httpClient)
        onRequest("GET", "http://test-url:8080/test") { rs -> rs.withCode(200) }
        result = client.invoke<Any>("test")
        Assertions.assertThat(result).hasToString("Rs200")

        reset(httpClient)
        onRequest("GET", "http://test-url:8080/test") { rs -> rs.withCode(201) }
        Assertions.assertThatThrownBy { client.invoke<Any>("test") }.isInstanceOf(HttpClientResponseException::class.java)
    }

    @Test
    @SuppressWarnings("unchecked")
    fun testBlockingRequestBody() {
        val mapper = Mockito.mock(HttpClientRequestMapper::class.java) as HttpClientRequestMapper<String>
        val client = compile(
            listOf(mapper), """
            @HttpClient
            interface TestClient {
              @HttpRoute(method = "POST", path = "/test")
              fun request(body: String)
            }
            """.trimIndent()
        )
        val ctx = Context.current()
        whenever(mapper.apply(ArgumentMatchers.any(), ArgumentMatchers.any()))
            .thenAnswer { HttpBody.plaintext("test-value") }
        onRequest("POST", "http://test-url:8080/test") { rs -> rs.withCode(200) }
        client.invoke<Unit>("request", "test-value")
        Mockito.verify(mapper).apply(ArgumentMatchers.same(ctx), ArgumentMatchers.eq("test-value"))

        reset(httpClient, mapper)
        Assertions.setMaxStackTraceElementsDisplayed(1000)
        whenever(mapper.apply(ArgumentMatchers.any(), ArgumentMatchers.any()))
            .thenAnswer { throw Exception() }
        onRequest("POST", "http://test-url:8080/test") { rs -> rs.withCode(200) }
        Assertions.assertThatThrownBy { client.invoke<Unit>("request", "test-value") }.isInstanceOf(HttpClientEncoderException::class.java)
        Mockito.verify(mapper).apply(ArgumentMatchers.same(ctx), ArgumentMatchers.eq("test-value"))
    }

    @Test
    fun testSuperinterfacesSupported() {
        val client = compile(
            listOf<Any>(), """
            @HttpClient
            interface TestClient: TestBase {
              @HttpRoute(method = "POST", path = "/test")
              fun request()
            }
            
            """.trimIndent(), """
            interface TestBase {
              @HttpRoute(method = "POST", path = "/test1")
              fun request1()
            }
            
            """.trimIndent()
        )

        onRequest("POST", "http://test-url:8080/test") { rs -> rs.withCode(200) }
        client.invoke<Unit>("request")
        reset(httpClient)

        onRequest("POST", "http://test-url:8080/test1") { rs -> rs.withCode(200) }
        client.invoke<Unit>("request1")
    }

}
