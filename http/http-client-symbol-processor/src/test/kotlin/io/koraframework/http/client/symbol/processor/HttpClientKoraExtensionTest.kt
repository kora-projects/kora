package io.koraframework.http.client.symbol.processor

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import io.koraframework.kora.app.ksp.KoraAppProcessorProvider
import io.koraframework.ksp.common.AbstractSymbolProcessorTest
import io.koraframework.ksp.common.GraphUtil.toGraphDraw

class HttpClientKoraExtensionTest : AbstractSymbolProcessorTest() {
    override fun commonImports(): String {
        return super.commonImports() + """
          import io.koraframework.config.common.mapper.ConfigValueMapper
          import io.koraframework.common.Either
          import io.koraframework.http.client.common.HttpClient
          import io.koraframework.http.client.common.telemetry.HttpClientTelemetryFactory
          import io.koraframework.http.client.common.response.HttpClientResponseMapper
          import io.koraframework.http.common.HttpResponseEntity
          import io.koraframework.json.common.JsonReader
          import io.koraframework.json.common.annotation.Json
        """.trimIndent()
    }

    @Test
    fun testExtension() {
        compile0(
            listOf(KoraAppProcessorProvider(), HttpClientSymbolProcessorProvider()),
            """
            @io.koraframework.common.annotation.KoraApp
            interface TestApp {
               fun client(): HttpClient = org.mockito.Mockito.mock(HttpClient::class.java)
               fun telemetry(): HttpClientTelemetryFactory = org.mockito.Mockito.mock(HttpClientTelemetryFactory::class.java)
               fun config(): io.koraframework.config.common.Config = org.mockito.Mockito.mock(io.koraframework.config.common.Config::class.java)
               fun extractor(): ConfigValueMapper<`${'$'}TestClient_Config`> = org.mockito.Mockito.mock(ConfigValueMapper::class.java) as ConfigValueMapper<`${'$'}TestClient_Config`>
            
                @io.koraframework.common.annotation.Root
                fun root(m: TestClient) = ""
            }
            """.trimIndent(), """
                    @io.koraframework.http.client.common.annotation.HttpClient
                    interface TestClient {
                      @io.koraframework.http.common.annotation.HttpRoute(method = "POST", path = "/")
                      fun test()
                    }
                    """.trimIndent()
        )
        compileResult.assertSuccess()
        val graph = loadClass("TestAppGraph").toGraphDraw()
        Assertions.assertThat(graph.nodes)
            .hasSize(7)
    }


    @Test
    fun testExtensionWithTag() {
        compile0(
            listOf(KoraAppProcessorProvider()), """
            @KoraApp
            interface App {
                @Root
                fun root(@Tag(String::class) mapper: HttpClientResponseMapper<HttpResponseEntity<String>>): String = ""
            
                @Tag(String::class)
                fun mapper() : HttpClientResponseMapper<String> = HttpClientResponseMapper<String> { rs -> "" }
            }
            """
        )

        compileResult.assertSuccess()
    }

    @Test
    fun testExtensionWithoutTag() {
        compile0(
            listOf(KoraAppProcessorProvider()), """
            @KoraApp
            interface App {
                @Root
                fun root(mapper: HttpClientResponseMapper<HttpResponseEntity<String>>): String = ""
            
                fun mapper() : HttpClientResponseMapper<String> = HttpClientResponseMapper<String> { rs -> "" }
            }
            """
        )

        compileResult.assertSuccess()
    }

    @Test
    fun testExtensionNullable() {
        compile0(
            listOf(KoraAppProcessorProvider()), """
            @KoraApp
            interface App {
                @Root
                fun root(mapper: HttpClientResponseMapper<HttpResponseEntity<String?>>): String = ""
            
                fun mapper() : HttpClientResponseMapper<String> = HttpClientResponseMapper<String> { rs -> "" }
            }
            """
        )

        compileResult.assertSuccess()
    }

    @Test
    fun testExtensionJsonEither() {
        compile0(
            listOf(KoraAppProcessorProvider()), """
            @KoraApp
            interface App {
                @Root
                fun root(@Json mapper: HttpClientResponseMapper<Either<String, String>>): String = ""

                fun reader(): JsonReader<String> = JsonReader { "" }
            }
            """
        )

        compileResult.assertSuccess()
    }

    @Test
    fun testExtensionJsonEitherTypeArguments() {
        compile0(
            listOf(KoraAppProcessorProvider()), """
            @KoraApp
            interface App {
                @Root
                fun root(mapper: HttpClientResponseMapper<Either<@Json String, @Json String>>): String = ""

                fun reader(): JsonReader<String> = JsonReader { "" }
            }
            """
        )

        compileResult.assertSuccess()
    }

    @Test
    fun testExtensionJsonEitherResponseEntity() {
        compile0(
            listOf(KoraAppProcessorProvider()), """
            @KoraApp
            interface App {
                @Root
                fun root(@Json mapper: HttpClientResponseMapper<HttpResponseEntity<Either<String, String>>>): String = ""

                fun reader(): JsonReader<String> = JsonReader { "" }
            }
            """
        )

        compileResult.assertSuccess()
    }

    @Test
    fun testExtensionJsonEitherResponseEntityTypeArguments() {
        compile0(
            listOf(KoraAppProcessorProvider()), """
            @KoraApp
            interface App {
                @Root
                fun root(mapper: HttpClientResponseMapper<HttpResponseEntity<Either<@Json String, @Json String>>>): String = ""

                fun reader(): JsonReader<String> = JsonReader { "" }
            }
            """
        )

        compileResult.assertSuccess()
    }

}
