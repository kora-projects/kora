package ru.tinkoff.kora.http.client.symbol.processor

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import ru.tinkoff.kora.kora.app.ksp.KoraAppProcessorProvider
import ru.tinkoff.kora.ksp.common.AbstractSymbolProcessorTest
import ru.tinkoff.kora.ksp.common.GraphUtil.toGraphDraw

class HttpClientKoraExtensionTest : AbstractSymbolProcessorTest() {
    override fun commonImports(): String {
        return super.commonImports() + """
          import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor
          import ru.tinkoff.kora.http.client.common.HttpClient
          import ru.tinkoff.kora.http.client.common.telemetry.HttpClientTelemetryFactory
          import ru.tinkoff.kora.http.client.common.response.HttpClientResponseMapper
          import ru.tinkoff.kora.http.common.HttpResponseEntity
        """.trimIndent()
    }

    @Test
    fun testExtension() {
        compile0(
            listOf(KoraAppProcessorProvider(), HttpClientSymbolProcessorProvider()),
            """
            @ru.tinkoff.kora.common.KoraApp
            interface TestApp {
               fun client(): HttpClient = org.mockito.Mockito.mock(HttpClient::class.java)
               fun telemetry(): HttpClientTelemetryFactory = org.mockito.Mockito.mock(HttpClientTelemetryFactory::class.java)
               fun config(): ru.tinkoff.kora.config.common.Config = org.mockito.Mockito.mock(ru.tinkoff.kora.config.common.Config::class.java)
               fun extractor(): ConfigValueExtractor<`${'$'}TestClient_Config`> = org.mockito.Mockito.mock(ConfigValueExtractor::class.java) as ConfigValueExtractor<`${'$'}TestClient_Config`>
            
                @ru.tinkoff.kora.common.annotation.Root
                fun root(m: TestClient) = ""
            }
            """.trimIndent(), """
                    @ru.tinkoff.kora.http.client.common.annotation.HttpClient
                    interface TestClient {
                      @ru.tinkoff.kora.http.common.annotation.HttpRoute(method = "POST", path = "/")
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


}
