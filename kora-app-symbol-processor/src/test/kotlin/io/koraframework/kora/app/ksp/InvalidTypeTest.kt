package io.koraframework.kora.app.ksp

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import io.koraframework.ksp.common.AbstractSymbolProcessorTest

class InvalidTypeTest : AbstractSymbolProcessorTest() {
    @Test
    fun testUnknownTypeComponent() {
        compile0(listOf(KoraAppProcessorProvider()),
            """
                @io.koraframework.common.KoraApp
                interface TestApp {
                    @Root
                    fun root() = Any()
                    fun unknownTypeComponent(): some.unknown.type.Component {
                        return null!!
                    }
                }
                
                """.trimIndent()
        )

        val failureMessages = compileResult.assertFailure().messages
        assertThat(failureMessages).anyMatch { it.endsWith("TestApp.kt:13:33: error: unresolved reference 'some'.") }
    }

    @Test
    fun testUnknownTypeDependency() {
        compile0(listOf(KoraAppProcessorProvider()),
            """
                @io.koraframework.common.KoraApp
                interface TestApp {
                    @Root
                    fun root(dependency: some.unknown.type.Component) = Any()
                }
                
                """.trimIndent()
        )

        val failureMessages = compileResult.assertFailure().messages
        assertThat(failureMessages).anyMatch { it.endsWith("TestApp.kt:12:26: error: unresolved reference 'some'.") }
    }
}
