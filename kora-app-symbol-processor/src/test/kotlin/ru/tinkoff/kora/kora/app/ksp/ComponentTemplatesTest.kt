package ru.tinkoff.kora.kora.app.ksp

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test

class ComponentTemplatesTest : AbstractKoraAppProcessorTest() {
    @Test
    fun testComponent() {
        val draw = compile(
            """
            @KoraApp
            interface ExampleApplication {
                fun dep() = 10
                @Root
                fun test(testClass: TestClass<Int>) = ""
            }
            """.trimIndent(),
            """
            @Component
            class TestClass<T>(dep: T)
            """.trimIndent()
        )
        Assertions.assertThat(draw.nodes).hasSize(3)
        draw.init()
    }

    @Test
    fun testModule() {
        val draw = compile(
            """
            @KoraApp
            interface ExampleApplication {
                fun dep() = 10
                fun <T> test(dep: T) = TestClass<T>(dep)
                @Root
                fun test(testClass: TestClass<Int>) = ""
            }
            """.trimIndent(),
            """
            class TestClass<T>(dep: T)
            """.trimIndent()
        )
        Assertions.assertThat(draw.nodes).hasSize(3)
        draw.init()
    }

    @Test
    fun testOuterTypeParamsMatchCorrectly() {
        compile("""
                @KoraApp
                interface ExampleApplication {
                    fun <T> dependency1(): MyJsonWriterImpl<List<T>> {return MyJsonWriterImpl()}
                    interface MyJsonWriter<T>
                    class MyJsonWriterImpl<T> : MyJsonWriter<T>

                    @Root
                    fun root(o: MyJsonWriter<List<String>>) {}
                }
                """);
    }
}
