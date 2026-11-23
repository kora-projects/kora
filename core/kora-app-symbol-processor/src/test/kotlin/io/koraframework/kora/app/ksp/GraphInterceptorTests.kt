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

    @Test
    fun interceptorDeclaredAsGraphInterceptorInterfaceWithTag() {
        val draw = compile(
            """
            @KoraApp
            interface ExampleApplication {
                @Root
                fun root(@Tag(Tag1::class) d1: Ds, d0: Ds): Any {
                    check(d1.log.toString() == "[one]") { "t1=" + d1.log }
                    check(d0.log.toString() == "[]") { "t0=" + d0.log }
                    return ""
                }
                @Tag(Tag1::class)
                fun interceptor(): GraphInterceptor<Ds> = object : GraphInterceptor<Ds> {
                    override fun afterInit(value: Ds): Ds { value.log.add("one"); return value }
                    override fun beforeRelease(value: Ds): Ds = value
                }
                fun ds0(): Ds = Ds()
                @Tag(Tag1::class) fun ds1(): Ds = Ds()
            }
            """.trimIndent(),
            "class Tag1",
            "class Ds { val log = mutableListOf<String>() }"
        )
        draw.init()
    }

    @Test
    fun interceptorDeclaredAsGraphInterceptorInterfaceInUntaggedFactoryModule() {
        val draw = compile(
            """
            @KoraApp
            interface ExampleApplication {
                @Root
                fun root(d0: Ds): Any {
                    check(d0.log.toString() == "[zero]") { "t0=" + d0.log }
                    return ""
                }
                @FactoryModule fun zero(): Mig = Mig("zero")
                fun ds0(): Ds = Ds()
            }
            """.trimIndent(),
            "class Ds { val log = mutableListOf<String>() }",
            """
            class Mig(private val name: String) {
                fun interceptor(): GraphInterceptor<Ds> = object : GraphInterceptor<Ds> {
                    override fun afterInit(value: Ds): Ds { value.log.add(name); return value }
                    override fun beforeRelease(value: Ds): Ds = value
                }
            }
            """.trimIndent()
        )
        draw.init()
    }

    @Test
    fun interceptorDeclaredAsGraphInterceptorInterfaceInTaggedFactoryModules() {
        val draw = compile(
            """
            @KoraApp
            interface ExampleApplication {
                @Root
                fun root(@Tag(Tag1::class) d1: Ds, @Tag(Tag2::class) d2: Ds, d0: Ds): Any {
                    check(d1.log.toString() == "[one]") { "t1=" + d1.log }
                    check(d2.log.toString() == "[two]") { "t2=" + d2.log }
                    check(d0.log.toString() == "[zero]") { "t0=" + d0.log }
                    return ""
                }

                @FactoryModule fun zero(): Mig = Mig("zero")
                @Tag(Tag1::class) @FactoryModule fun one(): Mig = Mig("one")
                @Tag(Tag2::class) @FactoryModule fun two(): Mig = Mig("two")

                fun ds0(): Ds = Ds()
                @Tag(Tag1::class) fun ds1(): Ds = Ds()
                @Tag(Tag2::class) fun ds2(): Ds = Ds()
            }
            """.trimIndent(),
            "class Tag1",
            "class Tag2",
            "class Ds { val log = mutableListOf<String>() }",
            """
            class Mig(private val name: String) {
                @Tag(Tag.Factory::class)
                fun interceptor(): GraphInterceptor<Ds> = object : GraphInterceptor<Ds> {
                    override fun afterInit(value: Ds): Ds { value.log.add(name); return value }
                    override fun beforeRelease(value: Ds): Ds = value
                }
            }
            """.trimIndent()
        )
        draw.init()
    }

    @Test
    fun interceptorInheritingAfterInitFromGenericBase() {
        val draw = compile(
            """
            @KoraApp
            interface ExampleApplication {
                @Root
                fun root(@Tag(Tag1::class) d1: Ds, d0: Ds): Any {
                    check(d1.log.toString() == "[one]") { "t1=" + d1.log }
                    check(d0.log.toString() == "[zero]") { "t0=" + d0.log }
                    return ""
                }
                @FactoryModule fun zero(): Mig = Mig("zero")
                @Tag(Tag1::class) @FactoryModule fun one(): Mig = Mig("one")
                fun ds0(): Ds = Ds()
                @Tag(Tag1::class) fun ds1(): Ds = Ds()
            }
            """.trimIndent(),
            "class Tag1",
            "class Ds { val log = mutableListOf<String>() }",
            """
            abstract class LoggingInterceptor<T : Any>(private val name: String) : GraphInterceptor<T> {
                abstract fun log(value: T): MutableList<String>
                override fun afterInit(value: T): T { log(value).add(name); return value }
                override fun beforeRelease(value: T): T = value
            }
            """.trimIndent(),
            """
            class DsInterceptor(name: String) : LoggingInterceptor<Ds>(name) {
                override fun log(value: Ds) = value.log
            }
            """.trimIndent(),
            """
            class Mig(private val name: String) {
                @Tag(Tag.Factory::class)
                fun interceptor(): DsInterceptor = DsInterceptor(name)
            }
            """.trimIndent()
        )
        draw.init()
    }

    private fun testClassNode(nodes: List<*>) = nodes
        .map { it as NodeImpl<*> }
        .first { it.type().typeName.endsWith("ExampleApplication\$TestClass") }
}
