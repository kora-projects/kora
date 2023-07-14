package ru.tinkoff.kora.kora.app.ksp

import com.google.devtools.ksp.processing.SymbolProcessorProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.tinkoff.kora.ksp.common.AbstractSymbolProcessorTest

class InvalidTypeTest : AbstractSymbolProcessorTest() {
    @Test
    fun testUnknownTypeComponent() {
        compile(listOf<SymbolProcessorProvider>(KoraAppProcessorProvider()), """
            @ru.tinkoff.kora.common.KoraApp
            interface TestApp {
                @Root
                fun root() = Any()
                fun unknownTypeComponent(): some.unknown.type.Component {
                    return null!!
                }
            }
            
            """.trimIndent())

        assertThat(compileResult.isFailed()).isTrue
        assertThat(compileResult.messages).anyMatch { it.endsWith("TestApp.kt:13: Component type is not resolvable in the current round of processing") }
    }

    @Test
    fun testUnknownTypeDependency() {
        compile(listOf<SymbolProcessorProvider>(KoraAppProcessorProvider()), """
            @ru.tinkoff.kora.common.KoraApp
            interface TestApp {
                @Root
                fun root(dependency: some.unknown.type.Component) = Any()
            }
            
            """.trimIndent())

        assertThat(compileResult.isFailed()).isTrue
        assertThat(compileResult.messages).anyMatch { it.endsWith("TestApp.kt:12: Dependency type is not resolvable in the current round of processing") }
    }
}
