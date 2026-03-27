package io.koraframework.kora.app.ksp

import io.koraframework.application.graph.GraphCondition
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class ConditionalComponentTest : AbstractKoraAppProcessorTest() {
    class MatchesCondition : GraphCondition {
        override fun eval(): GraphCondition.ConditionResult {
            return GraphCondition.ConditionResult.Matched("test")
        }
    }

    class FailedCondition : GraphCondition {
        override fun eval(): GraphCondition.ConditionResult {
            return GraphCondition.ConditionResult.Failed("test")
        }
    }

    @Test
    fun testConditionalOnClass() {
        val draw = compile(
            """
            @KoraApp
            interface ExampleApplication {
                @Root
                fun root(o: TestInterface): Any { return o }

                @Tag(io.koraframework.kora.app.ksp.ConditionalComponentTest.MatchesCondition::class)
                fun matches(): GraphCondition { return io.koraframework.kora.app.ksp.ConditionalComponentTest.MatchesCondition() }

                @Tag(io.koraframework.kora.app.ksp.ConditionalComponentTest.FailedCondition::class)
                fun failed(): GraphCondition { return  io.koraframework.kora.app.ksp.ConditionalComponentTest.FailedCondition() }
            }
            """, """
            @Component
            @Conditional(tag = io.koraframework.kora.app.ksp.ConditionalComponentTest.MatchesCondition::class)
            class TestClass1 : TestInterface
            """, """
            @Component
            @Conditional(tag = io.koraframework.kora.app.ksp.ConditionalComponentTest.FailedCondition::class)
            class TestClass2 : TestInterface
            """, """
            interface TestInterface
            """
        )
        assertThat(draw.nodes).hasSize(5)
        val graph = draw.init()
        val class1Node = draw.nodes.first { it.type().toString().contains("TestClass1") }
        val class2Node = draw.nodes.first { it.type().toString().contains("TestClass2") }
        assertThat(graph.get(class1Node)).isNotNull()
        assertThatThrownBy { graph.get(class2Node) }
            .hasMessage("Node value was not initialized: test")
    }

    @Test
    fun testConditionFailedOnRoot() {
        val draw = compile(
            """
            @KoraApp
            interface ExampleApplication {
                @Root
                @Conditional(tag = io.koraframework.kora.app.ksp.ConditionalComponentTest.FailedCondition::class)
                fun root(o: TestInterface): Any { return o }

                @Tag(io.koraframework.kora.app.ksp.ConditionalComponentTest.FailedCondition::class)
                fun failed(): GraphCondition { return  io.koraframework.kora.app.ksp.ConditionalComponentTest.FailedCondition() }
            }
            """, """
            @Component
            class TestClass1 : TestInterface
            """, """
            interface TestInterface
            """
        )
        assertThat(draw.nodes).hasSize(3)
        val graph = draw.init()
        val conditionNode = draw.nodes.first { it.type() == GraphCondition::class.java }
        for (node in draw.nodes) {
            if (node == conditionNode) {
                assertThat(graph.get(node)).isNotNull()
            } else {
                assertThatThrownBy { graph.get(node) }
                    .hasMessage("Node value was not initialized: test")
            }
        }
    }

    @Test
    fun testConditionFailedOnRootWithIntermediateNode() {
        val draw = compile(
            """
            @KoraApp
            interface ExampleApplication {
                @Root
                @Conditional(tag = io.koraframework.kora.app.ksp.ConditionalComponentTest.FailedCondition::class)
                fun root(o: TestClass1): Any { return o }

                @Tag(io.koraframework.kora.app.ksp.ConditionalComponentTest.FailedCondition::class)
                fun failed(): GraphCondition { return  io.koraframework.kora.app.ksp.ConditionalComponentTest.FailedCondition() }
            }
            """, """
            @Component
            class TestClass1(v : TestClass2)
            """, """
            @Component
            @Conditional(tag = io.koraframework.kora.app.ksp.ConditionalComponentTest.FailedCondition::class)
            class TestClass2
            """
        )
        assertThat(draw.nodes).hasSize(4)
        val graph = draw.init()
        val conditionNode = draw.nodes.first { it.type() == GraphCondition::class.java }
        for (node in draw.nodes) {
            if (node == conditionNode) {
                assertThat(graph.get(node)).isNotNull()
            } else {
                assertThatThrownBy { graph.get(node) }
            }
        }
    }

    @Test
    fun testMultipleRootConditions() {
        val draw = compile(
            """
            @KoraApp
            interface ExampleApplication {
                @Root
                @Conditional(tag = io.koraframework.kora.app.ksp.ConditionalComponentTest.FailedCondition::class)
                fun root1(o: TestClass1): Int { return 1 }

                @Root
                @Conditional(tag = io.koraframework.kora.app.ksp.ConditionalComponentTest.MatchesCondition::class)
                fun root2(o: TestClass1): String { return "" }

                @Tag(io.koraframework.kora.app.ksp.ConditionalComponentTest.MatchesCondition::class)
                fun matches(): GraphCondition { return io.koraframework.kora.app.ksp.ConditionalComponentTest.MatchesCondition() }

                @Tag(io.koraframework.kora.app.ksp.ConditionalComponentTest.FailedCondition::class)
                fun failed(): GraphCondition { return  io.koraframework.kora.app.ksp.ConditionalComponentTest.FailedCondition() }
            }
            """, """
            @Component
            class TestClass1(v: TestClass2)
            """, """
            @Component
            class TestClass2
            """
        )
        assertThat(draw.nodes).hasSize(6)
        val graph = draw.init()
        for (node in draw.nodes) {
            if (node.type().equals(Integer::class.java)) {
                assertThatThrownBy { graph.get(node) }
            } else {
                assertThat(graph.get(node)).isNotNull()
            }
        }
    }

}
