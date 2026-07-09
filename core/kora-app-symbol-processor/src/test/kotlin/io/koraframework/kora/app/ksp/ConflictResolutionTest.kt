package io.koraframework.kora.app.ksp

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ConflictResolutionTest : AbstractKoraAppProcessorTest() {
    @Test
    fun testMultipleComponentSameType() {
        compile0(listOf(KoraAppProcessorProvider()),
            """
            interface TestInterface            
            """.trimIndent(),
            """
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

        compileResult.assertFailure()
    }

    @Test
    fun testDefaultComponentOverride() {
        compile0(listOf(KoraAppProcessorProvider()),
            """
            interface TestInterface            
            """.trimIndent(),
            """
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

        compileResult.assertSuccess()
    }

    @Test
    fun testDefaultComponentTemplateOverride() {
        compile0(listOf(KoraAppProcessorProvider()),
            """
            interface TestInterface <T>
            """.trimIndent(),
            """
                        class TestImpl1 <T> : TestInterface <T> {}
                        """.trimIndent(), """
                                    class TestImpl2 <T> : TestInterface <T> {}
                                    """.trimIndent(), """
                                                @KoraApp
                                                interface ExampleApplication {
                                                    @Root
                                                    fun root(t: TestInterface<String>) = ""
                                                
                                                    fun <T> testImpl1() = TestImpl1<T>()
                                    
                                                    @DefaultComponent
                                                    fun <T> testImpl2() = TestImpl2<T>()
                                                }
                                                """.trimIndent()
        )

        compileResult.assertSuccess()
    }

    @Test
    fun testDefaultComponentAnnotatedComponentUsedWhenNoOverride() {
        val draw = compile(
            """
            interface TestInterface
            """,
            """
            @Component
            @DefaultComponent
            class DefaultTestImpl : TestInterface
            """,
            """
            @KoraApp
            interface ExampleApplication {
                @Root
                fun root(t: TestInterface): Any = t
            }
            """
        )
        assertThat(draw.nodes).hasSize(2)

        val defaultImpl = loadClass("DefaultTestImpl")
        val graph = draw.init()
        val values = draw.nodes.map { graph.get(it) }
        assertThat(values).anyMatch { defaultImpl.isInstance(it) }
    }

    @Test
    fun testDefaultComponentAnnotatedComponentOverrideByComponentClass() {
        val draw = compile(
            """
            interface TestInterface
            """,
            """
            @Component
            @DefaultComponent
            class DefaultTestImpl : TestInterface
            """,
            """
            @Component
            class OverrideTestImpl : TestInterface
            """,
            """
            @KoraApp
            interface ExampleApplication {
                @Root
                fun root(t: TestInterface): Any = t
            }
            """
        )
        assertThat(draw.nodes).hasSize(2)

        val defaultImpl = loadClass("DefaultTestImpl")
        val overrideImpl = loadClass("OverrideTestImpl")
        val graph = draw.init()
        val values = draw.nodes.map { graph.get(it) }
        assertThat(values).anyMatch { overrideImpl.isInstance(it) }
        assertThat(values).noneMatch { defaultImpl.isInstance(it) }
    }

    @Test
    fun testDefaultComponentAnnotatedComponentOverrideByFactoryMethod() {
        val draw = compile(
            """
            interface TestInterface
            """,
            """
            @Component
            @DefaultComponent
            class DefaultTestImpl : TestInterface
            """,
            """
            class OverrideTestImpl : TestInterface
            """,
            """
            @KoraApp
            interface ExampleApplication {
                @Root
                fun root(t: TestInterface): Any = t

                fun testImpl() = OverrideTestImpl()
            }
            """
        )
        assertThat(draw.nodes).hasSize(2)

        val defaultImpl = loadClass("DefaultTestImpl")
        val overrideImpl = loadClass("OverrideTestImpl")
        val graph = draw.init()
        val values = draw.nodes.map { graph.get(it) }
        assertThat(values).anyMatch { overrideImpl.isInstance(it) }
        assertThat(values).noneMatch { defaultImpl.isInstance(it) }
    }

    @Test
    fun testDefaultComponentAnnotatedComponentOverrideBySameTagOnly() {
        val draw = compile(
            """
            interface TestInterface
            """,
            """
            class DefaultTag
            """,
            """
            class OverrideTag
            """,
            """
            @Component
            @DefaultComponent
            @Tag(DefaultTag::class)
            class DefaultTestImpl : TestInterface
            """,
            """
            @Component
            @Tag(OverrideTag::class)
            class OverrideTestImpl : TestInterface
            """,
            """
            @KoraApp
            interface ExampleApplication {
                @Root
                fun root(@Tag(DefaultTag::class) t: TestInterface): Any = t
            }
            """
        )
        assertThat(draw.nodes).hasSize(2)

        val defaultImpl = loadClass("DefaultTestImpl")
        val overrideImpl = loadClass("OverrideTestImpl")
        val graph = draw.init()
        val values = draw.nodes.map { graph.get(it) }
        assertThat(values).anyMatch { defaultImpl.isInstance(it) }
        assertThat(values).noneMatch { overrideImpl.isInstance(it) }
    }

    @Test
    fun testDefaultComponentAnnotatedComponentOverrideByComponentClassWithSameTag() {
        val draw = compile(
            """
            interface TestInterface
            """,
            """
            class TestTag
            """,
            """
            @Component
            @DefaultComponent
            @Tag(TestTag::class)
            class DefaultTestImpl : TestInterface
            """,
            """
            @Component
            @Tag(TestTag::class)
            class OverrideTestImpl : TestInterface
            """,
            """
            @KoraApp
            interface ExampleApplication {
                @Root
                fun root(@Tag(TestTag::class) t: TestInterface): Any = t
            }
            """
        )
        assertThat(draw.nodes).hasSize(2)

        val defaultImpl = loadClass("DefaultTestImpl")
        val overrideImpl = loadClass("OverrideTestImpl")
        val graph = draw.init()
        val values = draw.nodes.map { graph.get(it) }
        assertThat(values).anyMatch { overrideImpl.isInstance(it) }
        assertThat(values).noneMatch { defaultImpl.isInstance(it) }
    }

    @Test
    fun testDefaultComponentAnnotatedComponentOverrideByConditionalComponent() {
        val draw = compile(
            """
            interface TestInterface
            """,
            """
            @Component
            @DefaultComponent
            class DefaultTestImpl : TestInterface
            """,
            """
            @Component
            @Conditional(tag = io.koraframework.kora.app.ksp.ConditionalComponentTest.MatchesCondition::class)
            class OverrideTestImpl : TestInterface
            """,
            """
            @KoraApp
            interface ExampleApplication {
                @Root
                fun root(t: TestInterface): Any = t

                @Tag(io.koraframework.kora.app.ksp.ConditionalComponentTest.MatchesCondition::class)
                fun matches(): GraphCondition = io.koraframework.kora.app.ksp.ConditionalComponentTest.MatchesCondition()
            }
            """
        )
        assertThat(draw.nodes).hasSize(3)

        val defaultImpl = loadClass("DefaultTestImpl")
        val overrideImpl = loadClass("OverrideTestImpl")
        val graph = draw.init()
        val values = draw.nodes.map { graph.get(it) }
        assertThat(values).anyMatch { overrideImpl.isInstance(it) }
        assertThat(values).noneMatch { defaultImpl.isInstance(it) }
    }
}
