package ru.tinkoff.kora.config.symbol.processor

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.tinkoff.kora.config.ksp.processor.ConfigParserSymbolProcessorProvider
import ru.tinkoff.kora.config.ksp.processor.ConfigSourceSymbolProcessorProvider
import ru.tinkoff.kora.kora.app.ksp.KoraAppProcessorProvider
import ru.tinkoff.kora.ksp.common.AbstractSymbolProcessorTest
import ru.tinkoff.kora.ksp.common.GraphUtil.toGraph

class ConfigExtractorGeneratorExtensionTest : AbstractSymbolProcessorTest() {
    @Test
    fun testExtensionAnnotatedDataClass() {
        compile0(listOf(KoraAppProcessorProvider(), ConfigSourceSymbolProcessorProvider(), ConfigParserSymbolProcessorProvider()),
            """
            @KoraApp
            interface TestApp {
              @Root
              fun root(extractor: ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor<TestConfig>) = ""
            }
            
            """.trimIndent(), """
            @ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor
            data class TestConfig(val value: String)
            
            """.trimIndent()
        )

        compileResult.assertSuccess()
        val graph = loadClass("TestAppGraph").toGraph()
        assertThat(graph.draw.nodes)
            .hasSize(2)
    }

    @Test
    fun testExtensionAnnotatedInterface() {
        compile0(listOf(KoraAppProcessorProvider(), ConfigSourceSymbolProcessorProvider(), ConfigParserSymbolProcessorProvider()),
            """
            @KoraApp
            interface TestApp {
              @Root
              fun root(extractor: ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor<TestConfig>) = ""
            }
            
            """.trimIndent(), """
            @ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor
            interface TestConfig {
              fun value(): String
            }
            
            """.trimIndent()
        )

        compileResult.assertSuccess()
        val graph = loadClass("TestAppGraph").toGraph()
        assertThat(graph.draw.nodes)
            .hasSize(2)
    }

}
