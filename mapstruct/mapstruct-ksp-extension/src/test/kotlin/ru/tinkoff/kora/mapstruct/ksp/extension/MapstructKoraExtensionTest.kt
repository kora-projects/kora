package ru.tinkoff.kora.mapstruct.ksp.extension

import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import ru.tinkoff.kora.ksp.common.AbstractSymbolProcessorTest
import ru.tinkoff.kora.ksp.common.GraphUtil
import ru.tinkoff.kora.ksp.common.GraphUtil.toGraph
import java.util.*

class MapstructKoraExtensionTest : AbstractSymbolProcessorTest() {

    override fun commonImports(): String {
        return super.commonImports() + """
            import org.mapstruct.*
            import org.mapstruct.Mapping
            import ru.tinkoff.kora.mapstruct.ksp.extension.*
            
            """.trimIndent()
    }

    private fun compile(@Language("kotlin") vararg sources: String): GraphUtil.GraphContainer {
        val patchedSources = Arrays.copyOf(sources, sources.size + 1)

        @Language("kotlin")
        val main = """
                    @KoraApp
                    interface TestApp {
                        @Root
                        fun root(carMapper: CarMapper): String {
                            return ""
                        }
                    }
            """.trimIndent()

        patchedSources[sources.size] = main
        super.compile0(*patchedSources)
        compileResult.assertSuccess()
        return loadClass("TestAppGraph").toGraph()
    }

    /**
     * @see CarMapper
     */
    @Test
    fun testDocumentationExample() {
        val graph = compile()
        assertThat(graph.draw.size()).isEqualTo(2)
    }
}
