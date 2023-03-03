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
                fun root() = ru.tinkoff.kora.annotation.processor.common.MockLifecycle.empty()
                fun unknownTypeComponent(): some.unknown.type.Component {
                    return null!!
                }
            }
            
            """.trimIndent())

        assertThat(compileResult.isFailed()).isTrue
        assertThat(compileResult.messages).anyMatch { it.endsWith("TestApp.kt:12: Component type is not resolvable in the current round of processing") }
    }

    @Test
    fun testUnknownTypeDependency() {
        compile(listOf<SymbolProcessorProvider>(KoraAppProcessorProvider()), """
            @ru.tinkoff.kora.common.KoraApp
            interface TestApp {
                fun root(dependency: some.unknown.type.Component) = ru.tinkoff.kora.annotation.processor.common.MockLifecycle.empty()
            }
            
            """.trimIndent())

        assertThat(compileResult.isFailed()).isTrue
        assertThat(compileResult.messages).anyMatch { it.endsWith("TestApp.kt:11: Dependency type is not resolvable in the current round of processing") }
    }
}
