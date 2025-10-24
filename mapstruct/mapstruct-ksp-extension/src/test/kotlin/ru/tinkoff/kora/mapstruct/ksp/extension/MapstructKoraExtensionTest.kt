package ru.tinkoff.kora.mapstruct.ksp.extension

import org.assertj.core.api.Assertions.assertThat
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test
import ru.tinkoff.kora.kora.app.ksp.KoraAppProcessorProvider
import ru.tinkoff.kora.ksp.common.AbstractSymbolProcessorTest
import ru.tinkoff.kora.ksp.common.GraphUtil
import ru.tinkoff.kora.ksp.common.GraphUtil.toGraph
import ru.tinkoff.kora.ksp.common.KotlinCompilation
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
        KotlinCompilation()
            .withClasspathJar("mapstruct")
            .compile(listOf(KoraAppProcessorProvider()), *patchedSources)
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
