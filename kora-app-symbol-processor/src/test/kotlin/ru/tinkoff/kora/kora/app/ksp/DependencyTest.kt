package ru.tinkoff.kora.kora.app.ksp

import org.assertj.core.api.Assertions
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test
import ru.tinkoff.kora.application.graph.ApplicationGraphDraw
import ru.tinkoff.kora.ksp.common.AbstractSymbolProcessorTest
import java.util.*
import java.util.function.Supplier

open class DependencyTest : AbstractSymbolProcessorTest() {
    override fun commonImports() = super.commonImports() + """
        import ru.tinkoff.kora.annotation.processor.common.MockLifecycle;
        import ru.tinkoff.kora.application.graph.*;
        import java.util.Optional;
        
        """.trimIndent()


    protected fun compile(@Language("kotlin") vararg sources: String): ApplicationGraphDraw {
        val compileResult = compile(listOf(KoraAppProcessorProvider()), *sources)
        if (compileResult.isFailed()) {
            throw compileResult.compilationException()
        }

        val appClass = compileResult.loadClass("ExampleApplicationGraph")
        val `object` = appClass.getConstructor().newInstance() as Supplier<ApplicationGraphDraw>
        return `object`.get()
    }


    @Test
    open fun testDiscoveredFinalClassDependency() {
        val draw = compile(
            """
            @KoraApp
            interface ExampleApplication {
                class TestClass1
                
                @Root
                fun test(testClass: TestClass1) = ""
            }
            """.trimIndent()
        )
        Assertions.assertThat(draw.nodes).hasSize(2)
        draw.init().block()
    }

    @Test
    open fun testDiscoveredFinalClassDependencyWithTag() {
        val draw = compile(
            """
            @KoraApp
            interface ExampleApplication {
                @Tag(TestClass1::class)
                class TestClass1
                
                @Root
                fun test(@Tag(TestClass1::class) testClass: TestClass1) = ""
            }
            """.trimIndent()
        )
        Assertions.assertThat(draw.nodes).hasSize(2)
        draw.init().block()
    }

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
        Assertions.assertThat(compileResult.isFailed()).isTrue()
//        Assertions.assertThat<Diagnostic<out JavaFileObject?>>(compileResult.errors()).hasSize(1)
//        Assertions.assertThat(compileResult.errors().get(0).getMessage(Locale.ENGLISH)).startsWith(
//            "Required dependency was not found: " +
//                "@Tag(ru.tinkoff.kora.kora.app.annotation.processor.packageForDependencyTest.testDiscoveredFinalClassDependencyTaggedDependencyNoTagOnClass.ExampleApplication.TestClass1) " +
//                "ru.tinkoff.kora.kora.app.annotation.processor.packageForDependencyTest.testDiscoveredFinalClassDependencyTaggedDependencyNoTagOnClass.ExampleApplication.TestClass1"
//        )
    }

}
