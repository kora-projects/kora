package io.koraframework.config.symbol.processor

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import io.koraframework.config.ksp.processor.ConfigParserSymbolProcessorProvider
import io.koraframework.config.ksp.processor.ConfigSourceSymbolProcessorProvider
import io.koraframework.kora.app.ksp.KoraAppProcessorProvider
import io.koraframework.ksp.common.AbstractSymbolProcessorTest
import io.koraframework.ksp.common.GraphUtil.toGraph

class ConfigValueMapperGeneratorExtensionTest : AbstractSymbolProcessorTest() {
    @Test
    fun testExtensionAnnotatedDataClass() {
        compile0(listOf(KoraAppProcessorProvider(), ConfigSourceSymbolProcessorProvider(), ConfigParserSymbolProcessorProvider()),
            """
            @KoraApp
            interface TestApp {
              @Root
              fun root(mapper: io.koraframework.config.common.mapper.ConfigValueMapper<TestConfig>) = ""
            }
            
            """.trimIndent(), """
            @io.koraframework.config.common.annotation.ConfigMapper
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
              fun root(mapper: io.koraframework.config.common.mapper.ConfigValueMapper<TestConfig>) = ""
            }
            
            """.trimIndent(), """
            @io.koraframework.config.common.annotation.ConfigMapper
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
