package ru.tinkoff.kora.kora.app.ksp

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test

open class DependencyTest : AbstractKoraAppProcessorTest() {
    @Test
    open fun testDiscoveredFinalClassDependencyTaggedDependencyNoTagOnClass() {
        Assertions.assertThatThrownBy {
            compile(
                """
                @KoraApp
                interface ExampleApplication {
                    class TestClass1 
                    
                    @Root
                    fun test(@Tag(TestClass1::class) testClass: TestClass1) = ""
                }
                """.trimIndent()
            )
        }
        compileResult.assertFailure()
//        Assertions.assertThat<Diagnostic<out JavaFileObject?>>(compileResult.errors()).hasSize(1)
//        Assertions.assertThat(compileResult.errors().get(0).getMessage(Locale.ENGLISH)).startsWith(
//            "Required dependency wasn't found: " +
//                "@Tag(ru.tinkoff.kora.kora.app.annotation.processor.packageForDependencyTest.testDiscoveredFinalClassDependencyTaggedDependencyNoTagOnClass.ExampleApplication.TestClass1) " +
//                "ru.tinkoff.kora.kora.app.annotation.processor.packageForDependencyTest.testDiscoveredFinalClassDependencyTaggedDependencyNoTagOnClass.ExampleApplication.TestClass1"
//        )
    }

    @Test
    fun testCycleInGraphResolvedWithProxy() {
        compile(
            """
            @KoraApp
            interface ExampleApplication {
                fun class1(promise: Interface1): Class1 {
                    return Class1()
                }

                fun class2(promise: PromiseOf<Class1>): Class2 {
                    return Class2("")
                }

                @Root
                fun root(class2: Class2) = Any()

                interface Interface1 {
                    val someVal: String
                    fun method() {}
                    fun methodWithReservedNameParameter(`is`: String) {}
                    private fun privateFun() {}
                }
                class Class1
                class Class2(override val someVal: String) : Interface1
            }
        """.trimIndent()
        )
    }

    @Test
    fun testOptionalValueOf() {
        val draw = compile(
            """
            @KoraApp
            interface ExampleApplication {
                fun component1() = "test"
                
                @Root
                fun root1(t: java.util.Optional<ValueOf<String>>) = Any()
                @Root
                fun root2(t: java.util.Optional<ValueOf<Int>>) = Any()

            }
            """.trimIndent()
        )
        Assertions.assertThat(draw.nodes).hasSize(5)
    }

    @Test
    fun testOptional() {
        val draw = compile(
            """
            @KoraApp
            interface ExampleApplication {
                fun component1() = "test"
                
                @Root
                fun root1(t: java.util.Optional<String>) = Any()
                @Root
                fun root2(t: java.util.Optional<Int>) = Any()

            }
            """.trimIndent()
        )
        Assertions.assertThat(draw.nodes).hasSize(5)
    }

}
