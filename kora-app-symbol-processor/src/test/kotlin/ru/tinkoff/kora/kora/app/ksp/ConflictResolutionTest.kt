package ru.tinkoff.kora.kora.app.ksp

import com.google.devtools.ksp.processing.SymbolProcessorProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.tinkoff.kora.ksp.common.AbstractSymbolProcessorTest

class ConflictResolutionTest : AbstractSymbolProcessorTest() {
    @Test
    fun testMultipleComponentSameType() {
        compile(
            listOf<SymbolProcessorProvider>(KoraAppProcessorProvider()),
            """
            interface TestInterface            
            """.trimIndent(), """
            class TestImpl1 : TestInterface {}
            """.trimIndent(), """
            class TestImpl2 : TestInterface {}
            """.trimIndent(), """
            @KoraApp
            interface ExampleApplication {
                @Root
                fun root(t: TestInterface) = ""
                fun testImpl1() = TestImpl1()
                fun testImpl2() = TestImpl2()
            }
            
            """.trimIndent()
        )

        assertThat(compileResult.isFailed()).isTrue()
    }

    @Test
    fun testDefaultComponentOverride() {
        compile(
            listOf<SymbolProcessorProvider>(KoraAppProcessorProvider()),
            """
            interface TestInterface            
            """.trimIndent(), """
            class TestImpl1 : TestInterface {}
            """.trimIndent(), """
            class TestImpl2 : TestInterface {}
            """.trimIndent(), """
            @KoraApp
            interface ExampleApplication {
                @Root
                fun root(t: TestInterface) = ""
            
                fun testImpl1() = TestImpl1()

                @DefaultComponent
                fun testImpl2() = TestImpl2()
            }
            """.trimIndent()
        )

        assertThat(compileResult.isFailed()).isFalse()
    }
}
