package io.koraframework.kora.app.ksp

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import io.koraframework.application.graph.internal.NodeImpl
import kotlin.collections.get

class GraphInterceptorTests :AbstractKoraAppProcessorTest() {

    @Test
    fun interceptor() {
        val draw = compile(
            """
                import io.koraframework.application.graph.GraphInterceptor

                @KoraApp
                interface ExampleApplication {
                            
                    class TestRoot 
                    
                    @Component
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
                import io.koraframework.application.graph.GraphInterceptor
                import io.koraframework.ksp.common.TestAspect

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
                import io.koraframework.application.graph.GraphInterceptor

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
}
