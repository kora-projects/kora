package io.koraframework.kora.app.ksp

import io.koraframework.application.graph.ApplicationGraphDraw
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TypeAliasTest : AbstractKoraAppProcessorTest() {

    @Test
    fun typeAliasDependency() {
        val draw = compile(
            """
            typealias Alias = TestClass
            class TestClass
            """.trimIndent(),
            """
            @KoraApp
            interface ExampleApplication {
                fun testClass() = TestClass()
                @Root
                fun root(cls: Alias): Any = cls
            }
            """.trimIndent()
        )
        assertThat(draw.nodes).hasSize(2)
        draw.init()
    }

    @Test
    fun typeAliasFactoryReturnType() {
        val draw = compile(
            """
            typealias Alias = TestClass
            class TestClass
            """.trimIndent(),
            """
            @KoraApp
            interface ExampleApplication {
                fun testClass(): Alias = TestClass()
                @Root
                fun root(cls: TestClass): Any = cls
            }
            """.trimIndent()
        )
        assertThat(draw.nodes).hasSize(2)
        draw.init()
    }

    @Test
    fun typeAliasInComponentConstructor() {
        val draw = compile(
            """
            typealias Alias = TestClass
            class TestClass
            """.trimIndent(),
            """
            @Component
            @Root
            class TestComponent(val cls: Alias)
            """.trimIndent(),
            """
            @KoraApp
            interface ExampleApplication {
                fun testClass() = TestClass()
            }
            """.trimIndent()
        )
        assertThat(draw.nodes).hasSize(2)
        draw.init()
    }

    @Test
    fun genericTypeAliasDependency() {
        val draw = compile(
            """
            class Holder<T>(val t: T)
            typealias StringHolder = Holder<String>
            """.trimIndent(),
            """
            @KoraApp
            interface ExampleApplication {
                fun holder() = Holder("x")
                @Root
                fun root(h: StringHolder): Any = h
            }
            """.trimIndent()
        )
        assertThat(draw.nodes).hasSize(2)
        draw.init()
    }

    @Test
    fun genericTypeAliasWithTypeParameter() {
        val draw = compile(
            """
            class Holder<T>(val t: T)
            typealias Box<T> = Holder<T>
            typealias Alias = String
            """.trimIndent(),
            """
            @KoraApp
            interface ExampleApplication {
                fun holder() = Holder("x")
                @Root
                fun root(h: Box<Alias>): Any = h
            }
            """.trimIndent()
        )
        assertThat(draw.nodes).hasSize(2)
        draw.init()
    }

    @Test
    fun allOfTypeAlias() {
        val draw = compile(
            """
            typealias Alias = TestInterface
            interface TestInterface
            """.trimIndent(),
            """
            @KoraApp
            interface ExampleApplication {
                fun testInterface(): TestInterface = object : TestInterface {}
                @Root
                fun root(all: All<Alias>): String = all.count().toString()
            }
            """.trimIndent()
        )
        assertThat(rootValue(draw, String::class.java)).isEqualTo("1")
    }

    @Test
    fun factoryModuleReturningTypeAliasInKoraApp() {
        val draw = compile(
            """
            class TestClass
            class InnerModule {
                fun testClass(): TestClass = TestClass()
            }
            typealias InnerAlias = InnerModule
            """.trimIndent(),
            """
            @KoraApp
            interface ExampleApplication {
                @FactoryModule
                fun inner(): InnerAlias = InnerModule()
                @Root
                fun root(cls: TestClass): Any = cls
            }
            """.trimIndent()
        )
        assertThat(draw.nodes).hasSize(3)
        draw.init()
    }

    @Test
    fun factoryModuleReturningTypeAliasInModule() {
        val draw = compile(
            """
            class TestClass
            class InnerModule {
                fun testClass(): TestClass = TestClass()
            }
            typealias InnerAlias = InnerModule
            @Module
            interface OuterModule {
                @FactoryModule
                fun inner(): InnerAlias = InnerModule()
            }
            """.trimIndent(),
            """
            @KoraApp
            interface ExampleApplication {
                @Root
                fun root(cls: TestClass): Any = cls
            }
            """.trimIndent()
        )
        assertThat(draw.nodes).hasSize(3)
        draw.init()
    }

    @Test
    fun typeAliasTag() {
        val draw = compile(
            """
            class RealTag
            typealias AliasTag = RealTag
            """.trimIndent(),
            """
            @KoraApp
            interface ExampleApplication {
                @Tag(RealTag::class)
                fun s(): String = "tagged"
                @Root
                fun root(@Tag(AliasTag::class) s: String): StringBuilder = StringBuilder(s)
            }
            """.trimIndent()
        )
        assertThat(rootValue(draw, StringBuilder::class.java).toString()).isEqualTo("tagged")
    }

    private fun rootValue(draw: ApplicationGraphDraw, type: Class<*>): Any? {
        val graph = draw.init()
        return graph.get(draw.nodes.first { it.type() == type })
    }
}
