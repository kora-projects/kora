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
    fun testComponentWithNonPublicPrimaryConstructorUsesPublicConstructor() {
        val draw = compile(
            """
            @KoraApp
            interface ExampleApplication {
                fun value(): Int = 1
                @Root
                fun test(testClass: TestClass): Any = testClass
            }
            """.trimIndent(),
            """
            @Component
            class TestClass private constructor(val value: String) {
                constructor(value: Int) : this(value.toString())
            }
            """.trimIndent()
        )
        Assertions.assertThat(draw.nodes).hasSize(3)
        draw.init()
    }

    @Test
    fun testJavaComponent() {
        compile0(
            listOf(KoraAppProcessorProvider()),
            listOf(
                """
                @io.koraframework.common.annotation.Component
                public final class TestClass {
                    public TestClass(String value) {}
                }
                """.trimIndent()
            ),
            """
            @KoraApp
            interface ExampleApplication {
                fun value(): String = "value"
                @Root
                fun test(testClass: TestClass): Any = testClass
            }
            """.trimIndent()
        ).assertSuccess()
    }
}
