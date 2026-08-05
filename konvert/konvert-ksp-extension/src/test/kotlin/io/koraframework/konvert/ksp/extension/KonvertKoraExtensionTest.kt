package io.koraframework.konvert.ksp.extension

import org.assertj.core.api.Assertions.assertThat
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test
import io.koraframework.kora.app.ksp.KoraAppProcessorProvider
import io.koraframework.ksp.common.AbstractSymbolProcessorTest
import io.koraframework.ksp.common.GraphUtil
import io.koraframework.ksp.common.GraphUtil.toGraph
import java.util.*

class KonvertKoraExtensionTest : AbstractSymbolProcessorTest() {

    override fun commonImports(): String {
        return super.commonImports() + """
            import io.mcarle.konvert.api.Konverter
            import io.koraframework.konvert.ksp.extension.*

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
        super.compile0(listOf(KoraAppProcessorProvider()), *patchedSources)
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

    @Test
    fun plainInterfaceWithoutKonverterIsNotProvided() {
        super.compile0(
            listOf(KoraAppProcessorProvider()),
            """
            interface NotAMapper {
                fun map(value: String): String
            }
            """.trimIndent(),
            """
            @KoraApp
            interface TestApp {
                @Root
                fun root(mapper: NotAMapper): String {
                    return ""
                }
            }
            """.trimIndent()
        )
        compileResult.assertFailure()
    }
}
