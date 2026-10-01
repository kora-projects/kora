package io.koraframework.kora.app.ksp

import io.koraframework.aop.symbol.processor.AopSymbolProcessorProvider
import io.koraframework.application.graph.internal.NodeImpl
import io.koraframework.ksp.common.CompilationErrorException
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test

class GraphInterceptorTests : AbstractKoraAppProcessorTest() {

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
                        override fun afterInit(value: TestClass) = value

                        override fun beforeRelease(value: TestClass) = value
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
                        override fun afterInit(value: TestClass) = value

                        override fun beforeRelease(value: TestClass) = value
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
    fun componentDeclaredAsAopProxyFails() {
        Assertions.assertThatThrownBy {
            compile(
                """
                    import io.koraframework.ksp.common.TestAspect

                    @KoraApp
                    interface ExampleApplication {

                        class TestRoot

                        open class TestClass {

                            @TestAspect
                            open fun getSome() = "1"
                        }

                        fun testClass(): `${'$'}ExampleApplication_TestClass__AopProxy` = `${'$'}ExampleApplication_TestClass__AopProxy`()

                        @Root
                        fun root(testClass: `${'$'}ExampleApplication_TestClass__AopProxy`) = TestRoot()
                    }
                    """.trimIndent(),
            )
        }
            .isInstanceOf(CompilationErrorException::class.java)
            .hasMessageContaining("Component provider returns a generated AOP proxy type")
            .hasMessageContaining("Declare the return type as the original type: ")
            .hasMessageContaining(".componentDeclaredAsAopProxyFails.ExampleApplication.TestClass")
    }

    @Test
    fun submoduleDeclaresAopProxyComponentWithOriginalType() {
        compile0(
            listOf(AopSymbolProcessorProvider(), KoraAppProcessorProvider(), KoraSubmoduleProcessorProvider()),
            """
                import io.koraframework.ksp.common.TestAspect

                @io.koraframework.common.annotation.KoraSubmodule
                interface ExampleSubmodule {

                    @Component
                    open class TestClass {

                        @TestAspect
                        open fun getSome() = "1"
                    }
                }
                """.trimIndent(),
        ).assertSuccess()

        val submodule = loadClass("ExampleSubmoduleSubmoduleImpl")
        val testClass = loadClass("ExampleSubmodule\$TestClass")
        Assertions.assertThat(submodule.getMethod("_component0").returnType).isEqualTo(testClass)
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
                        override fun afterInit(value: TestRoot) = value

                        override fun beforeRelease(value: TestRoot) = value
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
                import io.koraframework.application.graph.GraphInterceptor

                @KoraApp
                interface ExampleApplication {
                    class TestRoot
                    class TestTag
                    class TestClass

                    class UntaggedInterceptor : GraphInterceptor<TestClass> {
                        override fun afterInit(value: TestClass) = value

                        override fun beforeRelease(value: TestClass) = value
                    }

                    class TaggedInterceptor : GraphInterceptor<TestClass> {
                        override fun afterInit(value: TestClass) = value

                        override fun beforeRelease(value: TestClass) = value
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
        val node = testClassNode(draw.nodes)
        Assertions.assertThat(node.interceptors).hasSize(1)
        Assertions.assertThat(node.interceptors[0].tag()?.simpleName).isEqualTo("TestTag")
    }

    @Test
    fun interceptorWithAnyTagInterceptsTaggedComponent() {
        val draw = compile(
            """
                import io.koraframework.application.graph.GraphInterceptor

                @KoraApp
                interface ExampleApplication {
                    class TestRoot
                    class TestTag
                    class TestClass

                    class AnyTagInterceptor : GraphInterceptor<TestClass> {
                        override fun afterInit(value: TestClass) = value

                        override fun beforeRelease(value: TestClass) = value
                    }

                    @Tag(TestTag::class)
                    fun testClass() = TestClass()

                    @Tag(Tag.Any::class)
                    fun anyTagInterceptor() = AnyTagInterceptor()

                    @Root
                    fun root(@Tag(TestTag::class) testClass: TestClass) = TestRoot()
                }
                """.trimIndent(),
        )
        Assertions.assertThat(draw.nodes).hasSize(3)
        draw.init()
        Assertions.assertThat(testClassNode(draw.nodes).interceptors).hasSize(1)
    }

    private fun testClassNode(nodes: List<*>) = nodes
        .map { it as NodeImpl<*> }
        .first { it.type().typeName.endsWith("ExampleApplication\$TestClass") }
}
