package ru.tinkoff.kora.kora.app.ksp

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import ru.tinkoff.kora.application.graph.internal.NodeImpl

class GraphInterceptorTests :AbstractKoraAppProcessorTest() {

    @Test
    fun interceptor() {
        val draw = compile(
            """
                import ru.tinkoff.kora.application.graph.GraphInterceptor

                @KoraApp
                interface ExampleApplication {
                            
                    class TestRoot 
                    
                    class TestClass
                    
                    class TestInterceptor : GraphInterceptor<TestClass> {
                        override fun init(value: TestClass) = value

                        override fun release(value: TestClass) = value
                    }

                    @Root
                    fun root(testClass: TestClass) = TestRoot()
                    
                    fun interceptor(): TestInterceptor = TestInterceptor()
                }
                """.trimIndent(),
        )
        Assertions.assertThat(draw.nodes).hasSize(3)
        draw.init()
        Assertions.assertThat((draw.nodes[1] as NodeImpl<*>).interceptors).hasSize(1)
    }

    @Test
    fun interceptorForAopParent() {
        val draw = compile(
            """
                import ru.tinkoff.kora.application.graph.GraphInterceptor
                import ru.tinkoff.kora.ksp.common.TestAspect

                @KoraApp
                interface ExampleApplication {
                            
                    class TestRoot 
                    
                    @Component
                    open class TestClass {
                               
                        @TestAspect
                        open fun getSome() = "1"
                    }
                    
                    class TestInterceptor : GraphInterceptor<TestClass> {
                        override fun init(value: TestClass) = value

                        override fun release(value: TestClass) = value
                    }

                    @Root
                    fun root(testClass: TestClass) = TestRoot()
                    
                    fun interceptor(): TestInterceptor = TestInterceptor()
                }
                """.trimIndent(),
        )
        Assertions.assertThat(draw.nodes).hasSize(3)
        val init = draw.init()

        val node = draw.nodes[1] as NodeImpl<*>
        Assertions.assertThat(node.interceptors).hasSize(1)
        val value = node.factory[init]
        Assertions.assertThat(value.javaClass.simpleName).isEqualTo("\$ExampleApplication_TestClass__AopProxy")
    }

    @Test
    fun interceptorForRoot() {
        val draw = compile(
            """
                import ru.tinkoff.kora.application.graph.GraphInterceptor

                @KoraApp
                interface ExampleApplication {
                            
                    class TestRoot 
                    
                    class TestInterceptor : GraphInterceptor<TestRoot> {
                        override fun init(value: TestRoot) = value

                        override fun release(value: TestRoot) = value
                    }

                    @Root
                    fun root() = TestRoot()
                    
                    fun interceptor(): TestInterceptor = TestInterceptor()
                }
                """.trimIndent(),
        )
        Assertions.assertThat(draw.nodes).hasSize(2)
        draw.init()
        Assertions.assertThat((draw.nodes[1] as NodeImpl<*>).interceptors).hasSize(1)
    }

    @Test
    fun interceptorWithoutTagDoesNotInterceptTaggedComponent() {
        val draw = compile(
            """
                import ru.tinkoff.kora.application.graph.GraphInterceptor
                import ru.tinkoff.kora.common.Tag

                @KoraApp
                interface ExampleApplication {
                    class TestRoot
                    class TestTag
                    class TestClass

                    class UntaggedInterceptor : GraphInterceptor<TestClass> {
                        override fun init(value: TestClass) = value

                        override fun release(value: TestClass) = value
                    }

                    class TaggedInterceptor : GraphInterceptor<TestClass> {
                        override fun init(value: TestClass) = value

                        override fun release(value: TestClass) = value
                    }

                    @Tag(TestTag::class)
                    fun testClass() = TestClass()

                    fun untaggedInterceptor() = UntaggedInterceptor()

                    @Tag(TestTag::class)
                    fun taggedInterceptor() = TaggedInterceptor()

                    @Root
                    fun root(@Tag(TestTag::class) testClass: TestClass) = TestRoot()
                }
                """.trimIndent(),
        )
        Assertions.assertThat(draw.nodes).hasSize(4)
        draw.init()
        val taggedClassNode = draw.nodes
            .map { it as NodeImpl<*> }
            .first { node ->
                node.type().typeName.endsWith(".ExampleApplication\$TestClass")
                    && node.tags().size == 1
                    && node.tags()[0].simpleName == "TestTag"
            }
        Assertions.assertThat(taggedClassNode.interceptors).hasSize(1)
        Assertions.assertThat(taggedClassNode.interceptors[0].tags()).hasSize(1)
    }
}
