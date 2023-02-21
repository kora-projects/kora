package ru.tinkoff.kora.http.client.symbol.processor

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import reactor.core.publisher.Mono
import ru.tinkoff.kora.http.client.common.HttpClientResponseException
import ru.tinkoff.kora.http.client.common.response.HttpClientResponseMapper

class HttpClientResponseTest : AbstractHttpClientTest() {
    @Test
    fun testSimple() {
        val mapper = mock<HttpClientResponseMapper<String, Mono<String>>>()
        compile(listOf(mapper), """
            @HttpClient
            interface TestClient {
              @HttpRoute(method = "GET", path = "/test")
              fun test(): String
            }
        """.trimIndent())
        whenever(mapper.apply(any())).thenReturn(Mono.just("test-string"))

        val result = client.invoke("test")

        assertThat(result).isEqualTo("test-string")
        verify(mapper).apply(any())
    }

    @Test
    fun testCustomMapper() {
        compile(listOf<Any>(), """
            @HttpClient
            interface TestClient {
              @Mapping(TestMapper::class)
              @HttpRoute(method = "GET", path = "/test")
              fun test(): String
            }
        """.trimIndent(), """
            class TestMapper : HttpClientResponseMapper<String, Mono<String>> {
              override fun apply(rs: HttpClientResponse) = Mono.just("test-string-from-mapper")!!
            }
        """.trimIndent())

        val result = client.invoke("test")

        assertThat(result).isEqualTo("test-string-from-mapper")
    }

    @Test
    fun testOpenCustomMapper() {
        compile(listOf(newGenerated("TestMapper")), """
            @HttpClient
            interface TestClient {
              @Mapping(TestMapper::class)
              @HttpRoute(method = "GET", path = "/test")
              fun test(): String
            }
        """.trimIndent(), """
            open class TestMapper : HttpClientResponseMapper<String, Mono<String>> {
              override fun apply(rs: HttpClientResponse) = Mono.just("test-string-from-mapper")!!
            }
        """.trimIndent())

        val result = client.invoke("test")

        assertThat(result).isEqualTo("test-string-from-mapper")
    }

    @Test
    fun testCodeMapperByMapper() {
        compile(listOf<Any>(), """
            @HttpClient
            interface TestClient {
              @ResponseCodeMapper(code = 500, mapper = TestMapper::class)
              @HttpRoute(method = "GET", path = "/test")
              fun test(): String
            }
        """.trimIndent(), """
            class TestMapper : HttpClientResponseMapper<String, Mono<String>> {
              override fun apply(rs: HttpClientResponse) = Mono.just("test-string-from-mapper")!!
            }
        """.trimIndent())
        whenever(httpResponse.code()).thenReturn(500)

        val result = client.invoke("test")

        assertThat(result).isEqualTo("test-string-from-mapper")

        whenever(httpResponse.code()).thenReturn(200)
        assertThatThrownBy { client.invoke("test") }.isInstanceOf(HttpClientResponseException::class.java)
    }

    @Test
    fun testOpenCodeMapperByMapper() {
        compile(listOf(newGenerated("TestMapper")), """
            @HttpClient
            interface TestClient {
              @ResponseCodeMapper(code = 500, mapper = TestMapper::class)
              @HttpRoute(method = "GET", path = "/test")
              fun test(): String
            }
        """.trimIndent(), """
            open class TestMapper : HttpClientResponseMapper<String, Mono<String>> {
              override fun apply(rs: HttpClientResponse) = Mono.just("test-string-from-mapper")!!
            }
        """.trimIndent())
        whenever(httpResponse.code()).thenReturn(500)

        val result = client.invoke("test")

        assertThat(result).isEqualTo("test-string-from-mapper")

        whenever(httpResponse.code()).thenReturn(200)
        assertThatThrownBy { client.invoke("test") }.isInstanceOf(HttpClientResponseException::class.java)
    }

    @Test
    fun testCodeMapperByType() {
        compile(listOf(newGenerated("TestMapper200"), newGenerated("TestMapper500")), """
            @HttpClient
            interface TestClient {
              @ResponseCodeMapper(code = 200, type = TestRs200::class)
              @ResponseCodeMapper(code = 500, type = TestRs500::class)
              @HttpRoute(method = "GET", path = "/test")
              fun test(): TestResponse
            }
        """.trimIndent(), """
            class TestMapper200 : HttpClientResponseMapper<TestRs200, Mono<TestRs200>> {
              override fun apply(rs: HttpClientResponse) = Mono.just(TestRs200(""))!!
            }
            class TestMapper500 : HttpClientResponseMapper<TestRs500, Mono<TestRs500>> {
              override fun apply(rs: HttpClientResponse) = Mono.just(TestRs500(""))!!
            }
        """.trimIndent(), """
            sealed interface TestResponse
            data class TestRs200(val test: String) : TestResponse
            data class TestRs500(val test: String) : TestResponse
        """.trimIndent())
        var result = client.invoke("test")
        assertThat(result).isEqualTo(new("TestRs200", ""))

        whenever(httpResponse.code()).thenReturn(500)
        result = client.invoke("test")
        assertThat(result).isEqualTo(new("TestRs500", ""))

        whenever(httpResponse.code()).thenReturn(201)
        assertThatThrownBy { client.invoke("test") }.isInstanceOf(HttpClientResponseException::class.java)
    }
}
