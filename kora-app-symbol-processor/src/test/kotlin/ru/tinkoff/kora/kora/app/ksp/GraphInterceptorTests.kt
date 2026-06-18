package ru.tinkoff.kora.kora.app.ksp

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import ru.tinkoff.kora.application.graph.internal.NodeImpl

class GraphInterceptorTests : AbstractKoraAppProcessorTest() {

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
    fun interceptorForInterface() {
        val draw = compile(
            """
                import ru.tinkoff.kora.application.graph.GraphInterceptor

                @KoraApp
                interface ExampleApplication {
                            
                    interface TestInterface

                    class TestClass : TestInterface
                            
                    class TestRoot
                    
                    class TestInterceptor : GraphInterceptor<TestInterface> {
                        override fun init(value: TestInterface) = value

                        override fun release(value: TestInterface) = value
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
}
