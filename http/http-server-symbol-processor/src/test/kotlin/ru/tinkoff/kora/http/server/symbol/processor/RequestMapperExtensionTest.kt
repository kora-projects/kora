package ru.tinkoff.kora.http.server.symbol.processor

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import ru.tinkoff.kora.ksp.common.AbstractSymbolProcessorTest
import ru.tinkoff.kora.ksp.common.GraphUtil.toGraph

class RequestMapperExtensionTest : AbstractSymbolProcessorTest() {

    @Test
    fun testAsyncMapper() {
        compile0(
            """
    
            @ru.tinkoff.kora.common.KoraApp
            interface TestApp {
                fun asyncMapper() = ru.tinkoff.kora.http.server.common.handler.HttpServerRequestMapper<java.util.concurrent.CompletionStage<String>> { rs -> rs.body().asArrayStage().thenApply { it.decodeToString() }}
            
                @ru.tinkoff.kora.common.annotation.Root
                fun root(m: ru.tinkoff.kora.http.server.common.handler.HttpServerRequestMapper<String>) = ""
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()
        val graph = compileResult.loadClass("TestAppGraph").toGraph()
        Assertions.assertThat(graph.draw.nodes)
            .hasSize(3)
    }

}
