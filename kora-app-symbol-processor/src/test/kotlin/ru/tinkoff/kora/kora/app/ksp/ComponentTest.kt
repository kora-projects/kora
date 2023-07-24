package ru.tinkoff.kora.kora.app.ksp

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test

class ComponentTest : AbstractKoraAppProcessorTest() {
    @Test
    fun testComponent() {
        val draw = compile(
            """
            @KoraApp
            interface ExampleApplication {
                @Root
                fun test(testClass: TestClass) = ""
            }
            """.trimIndent(),
            """
            @Component
            open class TestClass()
            """.trimIndent()
        )
        Assertions.assertThat(draw.nodes).hasSize(2)
        draw.init().block()
    }

    @Test
    fun testAbstractComponent() {
        val draw = compile(
            """
            @KoraApp
            interface ExampleApplication {
                @Root
                fun test(testClass: TestClass, testInterface: TestInterface) = ""
            }
            """.trimIndent(),
            """
            @Component
            abstract class TestClass()
            """.trimIndent(),
            """
            @Component
            interface TestInterface {}
            """.trimIndent(),
            """
            @Component
            class TestClass1() : TestClass()
            """.trimIndent(),
            """
            @Component
            class TestClass2() : TestInterface
            """.trimIndent()
        )
        Assertions.assertThat(draw.nodes).hasSize(3)
        draw.init().block()
    }
}
