package io.koraframework.kora.app.ksp

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
        draw.init()
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
        draw.init()
    }

    @Test
    fun testInternalComponents() {
        val draw = compile(
            """
            @KoraApp
            interface ExampleApplication
            """.trimIndent(),
            """
            @Module
            internal interface InternalModule {
                fun box(): Box<Secret> = Box(Secret())
            }
            """.trimIndent(),
            """
            internal class Secret
            """.trimIndent(),
            """
            internal class Box<T>(val value: T)
            """.trimIndent(),
            """
            @Component
            @Root
            internal class TestClass(val box: Box<Secret>)
            """.trimIndent()
        )
        Assertions.assertThat(draw.nodes).hasSize(2)
        draw.init()
    }
}
