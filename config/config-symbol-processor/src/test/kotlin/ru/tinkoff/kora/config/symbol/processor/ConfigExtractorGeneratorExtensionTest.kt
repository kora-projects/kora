package ru.tinkoff.kora.config.symbol.processor

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.tinkoff.kora.ksp.common.AbstractSymbolProcessorTest
import ru.tinkoff.kora.ksp.common.GraphUtil.toGraph

class ConfigExtractorGeneratorExtensionTest : AbstractSymbolProcessorTest() {
    @Test
    fun testExtensionAnnotatedDataClass() {
        compile0(
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
        compile0(
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

    @Test
    fun testExtensionNonAnnotatedDataClass() {
        compile0(
            """
            @KoraApp
            interface TestApp {
              @Root
              fun root(extractor: ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor<TestConfig>) = ""
            }
            """.trimIndent(), """
            data class TestConfig(val value: String){}
            
            """.trimIndent()
        )
        compileResult.assertSuccess()

        val graph = loadClass("TestAppGraph").toGraph()
        assertThat(graph.draw.nodes)
            .hasSize(2)
    }

    @Test
    fun testExtensionNonAnnotatedInterface() {
        compile0(
            """
            @KoraApp
            interface TestApp {
              @Root
              fun root(extractor: ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor<TestConfig>) = ""
            }
            
            """.trimIndent(), """
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

    @Test
    fun testExtensionNonAnnotatedRecord() {
        compile0(
            """
            @KoraApp
            interface TestApp {
              @Root
              fun root(extractor: ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor<ru.tinkoff.kora.config.symbol.processor.cases.RecordConfig>) = ""
            }
            
            """.trimIndent()
        )
        compileResult.assertSuccess()

        val graph = loadClass("TestAppGraph").toGraph()
        assertThat(graph.draw.nodes)
            .hasSize(2)
    }

    @Test
    fun testExtensionNonAnnotatedPojo() {
        compile0(
            """
            @KoraApp
            interface TestApp {
              @Root
              fun root(extractor: ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor<ru.tinkoff.kora.config.symbol.processor.cases.PojoConfig>) = ""
            }
            
            """.trimIndent()
        )
        compileResult.assertSuccess()

        val graph = loadClass("TestAppGraph").toGraph()
        assertThat(graph.draw.nodes)
            .hasSize(2)
    }
}
