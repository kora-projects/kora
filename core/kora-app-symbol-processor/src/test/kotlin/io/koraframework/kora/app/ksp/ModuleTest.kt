package io.koraframework.kora.app.ksp

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

class ModuleTest : AbstractKoraAppProcessorTest() {

    @Test
    fun testAnnotatedModuleProvidesDependency() {
        val draw = compile(
            """
            @KoraApp
            interface ExampleApplication {
                @Root
                fun root(cls: TestClass): Any = cls
            }
            """.trimIndent(),
            """
            class TestClass
            """.trimIndent(),
            """
            @Module
            interface TestModule {
                fun testClass(): TestClass = TestClass()
            }
            """.trimIndent()
        )
        assertThat(draw.nodes).hasSize(2)
        draw.init()
    }

    @Test
    fun testMixedInModuleProvidesDependency() {
        val draw = compile(
            """
            @KoraApp
            interface ExampleApplication : TestMixin {
                @Root
                fun root(cls: TestClass): Any = cls
            }
            """.trimIndent(),
            """
            class TestClass
            """.trimIndent(),
            """
            interface TestMixin {
                fun testClass(): TestClass = TestClass()
            }
            """.trimIndent()
        )
        assertThat(draw.nodes).hasSize(2)
        draw.init()
    }

    @Test
    fun testMultipleAnnotatedModules() {
        val draw = compile(
            """
            @KoraApp
            interface ExampleApplication {
                @Root
                fun root(c1: TestClass1, c2: TestClass2): Any = c1
            }
            """.trimIndent(),
            """
            class TestClass1
            """.trimIndent(),
            """
            class TestClass2
            """.trimIndent(),
            """
            @Module
            interface Module1 {
                fun testClass1(): TestClass1 = TestClass1()
            }
            """.trimIndent(),
            """
            @Module
            interface Module2 {
                fun testClass2(): TestClass2 = TestClass2()
            }
            """.trimIndent()
        )
        assertThat(draw.nodes).hasSize(3)
        draw.init()
    }

    @Test
    fun testModuleInheritsFromBaseInterface() {
        val draw = compile(
            """
            @KoraApp
            interface ExampleApplication {
                @Root
                fun root(c1: TestClass1, c2: TestClass2): Any = c1
            }
            """.trimIndent(),
            """
            class TestClass1
            """.trimIndent(),
            """
            class TestClass2
            """.trimIndent(),
            """
            interface BaseModule {
                fun testClass1(): TestClass1 = TestClass1()
            }
            """.trimIndent(),
            """
            @Module
            interface ExtendedModule : BaseModule {
                fun testClass2(): TestClass2 = TestClass2()
            }
            """.trimIndent()
        )
        assertThat(draw.nodes).hasSize(3)
        draw.init()
    }

    @Test
    fun testModuleOverridesBaseInterfaceMethod() {
        val draw = compile(
            """
            @KoraApp
            interface ExampleApplication {
                @Root
                fun root(cls: TestClass): Any = cls
            }
            """.trimIndent(),
            """
            class TestClass
            """.trimIndent(),
            """
            interface BaseModule {
                fun testClass(): TestClass = throw IllegalStateException("base should not be called")
            }
            """.trimIndent(),
            """
            @Module
            interface OverrideModule : BaseModule {
                override fun testClass(): TestClass = TestClass()
            }
            """.trimIndent()
        )
        assertThat(draw.nodes).hasSize(2)
        draw.init()
    }

    @Test
    fun testNestedModuleInsideApp() {
        val draw = compile(
            """
            @KoraApp
            interface ExampleApplication {
                @Root
                fun root(cls: TestClass): Any = cls

                class TestClass

                @Module
                interface TestModule {
                    fun testClass(): TestClass = TestClass()
                }
            }
            """.trimIndent()
        )
        assertThat(draw.nodes).hasSize(2)
        draw.init()
    }

    @Test
    fun testModuleWithTaggedComponent() {
        val draw = compile(
            """
            @KoraApp
            interface ExampleApplication {
                @Root
                fun root(@Tag(TestModule::class) cls: TestClass): Any = cls
            }
            """.trimIndent(),
            """
            class TestClass
            """.trimIndent(),
            """
            @Module
            interface TestModule {
                @Tag(TestModule::class)
                fun testClass(): TestClass = TestClass()
            }
            """.trimIndent()
        )
        assertThat(draw.nodes).hasSize(2)
        draw.init()
    }

    @Disabled("Haven't decided whether to release it yet")
    @Test
    fun testClassModuleProvidesDependency() {
        val draw = compile(
            """
            @KoraApp
            interface ExampleApplication {
                @Root
                fun root(cls: TestClass): Any = cls
            }
            """.trimIndent(),
            """
            class TestClass
            """.trimIndent(),
            """
            @Module
            class TestModule {
                fun testClass(): TestClass = TestClass()
            }
            """.trimIndent()
        )
        // 3 nodes: root, TestClass (from module method), TestModule (module instance)
        assertThat(draw.nodes).hasSize(3)
        draw.init()
    }

    @Disabled("Haven't decided whether to release it yet")
    @Test
    fun testClassModuleWithConstructorDependency() {
        val draw = compile(
            """
            @KoraApp
            interface ExampleApplication {
                @Root
                fun root(cls: TestClass): Any = cls
            }
            """.trimIndent(),
            """
            class TestClass
            """.trimIndent(),
            """
            class Dep
            """.trimIndent(),
            """
            @Module
            class TestModule(private val dep: Dep) {
                fun testClass(): TestClass = TestClass()
            }
            """.trimIndent(),
            """
            @Module
            interface DepProvider {
                fun dep(): Dep = Dep()
            }
            """.trimIndent()
        )
        assertThat(draw.nodes).hasSize(4)
        draw.init()
    }

    @Disabled("Haven't decided whether to release it yet")
    @Test
    fun testClassModuleExtendsBaseModule() {
        val draw = compile(
            """
            @KoraApp
            interface ExampleApplication {
                @Root
                fun root(a: A, b: B): Any = a
            }
            """.trimIndent(),
            """
            class A
            """.trimIndent(),
            """
            class B
            """.trimIndent(),
            """
            open class BaseModule {
                fun b(): B = B()
            }
            """.trimIndent(),
            """
            @Module
            class ConcreteModule : BaseModule() {
                fun a(): A = A()
            }
            """.trimIndent()
        )
        // 4 nodes: root, A (from ConcreteModule.a()), B (inherited from BaseModule.b()), ConcreteModule instance
        assertThat(draw.nodes).hasSize(4)
        draw.init()
    }

    @Disabled("Haven't decided whether to release it yet")
    @Test
    fun testClassModuleOverridesInheritedMethod() {
        val draw = compile(
            """
            @KoraApp
            interface ExampleApplication {
                @Root
                fun root(cls: TestClass): Any = cls
            }
            """.trimIndent(),
            """
            class TestClass
            """.trimIndent(),
            """
            open class BaseModule {
                open fun testClass(): TestClass = throw IllegalStateException("base must not be called")
            }
            """.trimIndent(),
            """
            @Module
            class ConcreteModule : BaseModule() {
                override fun testClass(): TestClass = TestClass()
            }
            """.trimIndent()
        )
        // 3 nodes: root, TestClass, ConcreteModule
        assertThat(draw.nodes).hasSize(3)
        draw.init()
    }

    @Test
    fun testMethodModuleProvidesDependency() {
        val draw = compile(
            """
            @KoraApp
            interface ExampleApplication {
                @Root
                fun root(cls: TestClass): Any = cls
            }
            """.trimIndent(),
            """
            class TestClass
            """.trimIndent(),
            """
            class InnerModule {
                fun testClass(): TestClass = TestClass()
            }
            """.trimIndent(),
            """
            @Module
            interface OuterModule {
                @FactoryModule
                fun inner(): InnerModule = InnerModule()
            }
            """.trimIndent()
        )
        // 3 nodes: root, TestClass (from inner), InnerModule (from outer.inner())
        assertThat(draw.nodes).hasSize(3)
        draw.init()
    }

    @Test
    fun testMethodModuleWithDependency() {
        val draw = compile(
            """
            @KoraApp
            interface ExampleApplication {
                @Root
                fun root(cls: TestClass): Any = cls
            }
            """.trimIndent(),
            """
            class TestClass
            """.trimIndent(),
            """
            class Config
            """.trimIndent(),
            """
            class InnerModule(private val config: Config) {
                fun testClass(): TestClass = TestClass()
            }
            """.trimIndent(),
            """
            @Module
            interface OuterModule {
                @FactoryModule
                fun inner(config: Config): InnerModule = InnerModule(config)
                fun config(): Config = Config()
            }
            """.trimIndent()
        )
        // 4 nodes: root, TestClass, InnerModule, Config
        assertThat(draw.nodes).hasSize(4)
        draw.init()
    }

    @Test
    fun testMethodModuleWithTag() {
        // @Tag on @FactoryModule applies to the InnerModule instance;
        // the tag is used when inner-module components resolve their module dependency
        val draw = compile(
            """
            @KoraApp
            interface ExampleApplication {
                @Root
                fun root(cls: TestClass): Any = cls
            }
            """.trimIndent(),
            """
            class TestClass
            """.trimIndent(),
            """
            class InnerModule {
                fun testClass(): TestClass = TestClass()
            }
            """.trimIndent(),
            """
            @Module
            interface OuterModule {
                @FactoryModule
                @Tag(OuterModule::class)
                fun inner(): InnerModule = InnerModule()
            }
            """.trimIndent()
        )
        // 3 nodes: root, TestClass (untagged, produced by the tagged InnerModule), InnerModule(@Tag(OuterModule))
        assertThat(draw.nodes).hasSize(3)
        draw.init()
    }

    @Test
    fun testMethodModuleInMixedIn() {
        val draw = compile(
            """
            @KoraApp
            interface ExampleApplication {
                @FactoryModule
                fun inner(): InnerModule = InnerModule()

                @Root
                fun root(cls: TestClass): Any = cls
            }
            """.trimIndent(),
            """
            class TestClass
            """.trimIndent(),
            """
            class InnerModule {
                fun testClass(): TestClass = TestClass()
            }
            """.trimIndent()
        )
        // 3 nodes: root, TestClass (from inner), InnerModule
        assertThat(draw.nodes).hasSize(3)
        draw.init()
    }

    @Test
    fun testMethodModuleInnerInheritsMethods() {
        val draw = compile(
            """
            @KoraApp
            interface ExampleApplication {
                @Root
                fun root(cls: TestClass): Any = cls
            }
            """.trimIndent(),
            """
            class TestClass
            """.trimIndent(),
            """
            open class BaseModule {
                fun testClass(): TestClass = TestClass()
            }
            """.trimIndent(),
            """
            class InnerModule : BaseModule()
            """.trimIndent(),
            """
            @Module
            interface OuterModule {
                @FactoryModule
                fun inner(): InnerModule = InnerModule()
            }
            """.trimIndent()
        )
        // 3 nodes: root, TestClass (from BaseModule.testClass()), InnerModule
        assertThat(draw.nodes).hasSize(3)
        draw.init()
    }

    @Test
    fun testMultipleMethodModules() {
        val draw = compile(
            """
            @KoraApp
            interface ExampleApplication {
                @Root
                fun root(a: TestClass1, b: TestClass2): Any = a
            }
            """.trimIndent(),
            """
            class TestClass1
            """.trimIndent(),
            """
            class TestClass2
            """.trimIndent(),
            """
            class InnerModule1 {
                fun testClass1(): TestClass1 = TestClass1()
            }
            """.trimIndent(),
            """
            class InnerModule2 {
                fun testClass2(): TestClass2 = TestClass2()
            }
            """.trimIndent(),
            """
            @Module
            interface OuterModule {
                @FactoryModule
                fun inner1(): InnerModule1 = InnerModule1()
                @FactoryModule
                fun inner2(): InnerModule2 = InnerModule2()
            }
            """.trimIndent()
        )
        // 5 nodes: root, TestClass1, TestClass2, InnerModule1, InnerModule2
        assertThat(draw.nodes).hasSize(5)
        draw.init()
    }

    @Test
    fun testAppExtendsMultipleMixedInInterfaces() {
        val draw = compile(
            """
            @KoraApp
            interface ExampleApplication : TestMixin1, TestMixin2 {
                @Root
                fun root(c1: TestClass1, c2: TestClass2): Any = c1
            }
            """.trimIndent(),
            """
            class TestClass1
            """.trimIndent(),
            """
            class TestClass2
            """.trimIndent(),
            """
            interface TestMixin1 {
                fun testClass1(): TestClass1 = TestClass1()
            }
            """.trimIndent(),
            """
            interface TestMixin2 {
                fun testClass2(): TestClass2 = TestClass2()
            }
            """.trimIndent()
        )
        assertThat(draw.nodes).hasSize(3)
        draw.init()
    }

    @Test
    fun testFactoryModuleTagPropagation() {
        val draw = compile(
            """
            @KoraApp
            interface ExampleApplication {
                @Root
                fun root(@Tag(Tag1::class) t1: TestClass, @Tag(Tag2::class) t2: TestClass): Any = ""

                @Tag(Tag1::class)
                @FactoryModule
                fun testModule(): TestModule = TestModule()

                @Tag(Tag2::class)
                @FactoryModule
                fun testModule2(): TestModule = TestModule()

                @Tag(Tag1::class)
                fun testDependency1(): TestDependency = TestDependency()

                @Tag(Tag2::class)
                fun testDependency2(): TestDependency = TestDependency()
            }
            """.trimIndent(),
            """
            class Tag1
            """.trimIndent(),
            """
            class Tag2
            """.trimIndent(),
            """
            class TestClass
            """.trimIndent(),
            """
            class TestDependency
            """.trimIndent(),
            """
            class TestModule {
                @Tag(Tag.Factory::class)
                fun testClass(@Tag(Tag.Factory::class) dep: TestDependency): TestClass = TestClass()
            }
            """.trimIndent()
        )
        assertThat(draw.nodes).hasSize(7)
        draw.init()
    }
}
