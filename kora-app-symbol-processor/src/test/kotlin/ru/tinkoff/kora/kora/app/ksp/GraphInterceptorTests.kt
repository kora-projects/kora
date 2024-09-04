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
                import ch.qos.logback.classic.LoggerContext
                import org.slf4j.ILoggerFactory
                import ru.tinkoff.kora.logging.common.annotation.Log
                import ru.tinkoff.kora.application.graph.GraphInterceptor

                @KoraApp
                interface ExampleApplication {
                            
                    class TestRoot 
                    
                    @Component
                    open class TestClass {
                               
                        @Log
                        open fun getSome() = "1"
                    }
                    
                    class TestInterceptor : GraphInterceptor<TestClass> {
                        override fun init(value: TestClass) = value

                        override fun release(value: TestClass) = value
                    }

                    @Root
                    fun root(testClass: TestClass) = TestRoot()
                    
                    fun interceptor(): TestInterceptor = TestInterceptor()
                    
                    fun someLoggerFactory(): ILoggerFactory = LoggerContext()
                }
                """.trimIndent(),
        )
        Assertions.assertThat(draw.nodes).hasSize(4)
        draw.init()
        Assertions.assertThat((draw.nodes[2] as NodeImpl<*>).interceptors).hasSize(1)
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
}
