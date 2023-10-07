package ru.tinkoff.kora.http.client.symbol.processor

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import ru.tinkoff.kora.ksp.common.AbstractSymbolProcessorTest
import ru.tinkoff.kora.ksp.common.GraphUtil.toGraph

class ResponseMapperExtensionTest : AbstractSymbolProcessorTest() {

    @Test
    fun testAsyncMapper() {
        compile0(
            """
            import ru.tinkoff.kora.http.client.common.response.HttpClientResponseMapper
            import java.util.concurrent.CompletionStage
    
            @ru.tinkoff.kora.common.KoraApp
            interface TestApp {
                fun asyncMapper() = HttpClientResponseMapper<CompletionStage<String>> { rs -> rs.body().asArrayStage().thenApply { it.decodeToString() }}
            
                @ru.tinkoff.kora.common.annotation.Root
                fun root(m: HttpClientResponseMapper<String>) = ""
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()
        val graph = compileResult.loadClass("TestAppGraph").toGraph()
        Assertions.assertThat(graph.draw.nodes)
            .hasSize(3)
    }

}
