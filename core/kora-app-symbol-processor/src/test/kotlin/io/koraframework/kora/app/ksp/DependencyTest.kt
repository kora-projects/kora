package io.koraframework.kora.app.ksp

import io.koraframework.application.graph.PromiseOf
import io.koraframework.application.graph.ValueOf
import io.koraframework.ksp.common.CompilationErrorException
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

open class DependencyTest : AbstractKoraAppProcessorTest() {
    @Test
    open fun testDiscoveredFinalClassDependencyTaggedDependencyNoTagOnClass() {
        Assertions.assertThatThrownBy {
            compile(
                """
                @KoraApp
                interface ExampleApplication {
                    class TestClass1 
                    
                    @Root
                    fun test(@Tag(TestClass1::class) testClass: TestClass1) = ""
                }
                """.trimIndent()
            )
        }
        compileResult.assertFailure()
//        Assertions.assertThat<Diagnostic<out JavaFileObject?>>(compileResult.errors()).hasSize(1)
//        Assertions.assertThat(compileResult.errors().get(0).getMessage(Locale.ENGLISH)).startsWith(
//            "Required dependency wasn't found: " +
//                "@Tag(io.koraframework.kora.app.annotation.processor.packageForDependencyTest.testDiscoveredFinalClassDependencyTaggedDependencyNoTagOnClass.ExampleApplication.TestClass1) " +
//                "io.koraframework.kora.app.annotation.processor.packageForDependencyTest.testDiscoveredFinalClassDependencyTaggedDependencyNoTagOnClass.ExampleApplication.TestClass1"
//        )
    }

    @Test
    fun testCycleInGraphResolvedWithProxy() {
        compile(
            """
            @KoraApp
            interface ExampleApplication {
                fun class1(promise: Interface1): Class1 {
                    return Class1()
                }

                fun class2(promise: PromiseOf<Class1>): Class2 {
                    return Class2("")
                }

                @Root
                fun root(class2: Class2) = Any()

                interface Interface1 {
                    val someVal: String
                    fun method() {}
                    fun methodWithReservedNameParameter(`is`: String) {}
                    private fun privateFun() {}
                }
                class Class1
                class Class2(override val someVal: String) : Interface1
            }
        """.trimIndent()
        )
    }

    @Test
    fun testOptionalValueOf() {
        val draw = compile(
            """
            @KoraApp
            interface ExampleApplication {
                fun component1() = "test"
                
                @Root
                fun root1(t: java.util.Optional<ValueOf<String>>) = Any()
                @Root
                fun root2(t: java.util.Optional<ValueOf<Int>>) = Any()

            }
            """.trimIndent()
        )
        Assertions.assertThat(draw.nodes).hasSize(5)
    }

    @Test
    fun testOptional() {
        val draw = compile(
            """
            @KoraApp
            interface ExampleApplication {
                fun component1() = "test"
                
                @Root
                fun root1(t: java.util.Optional<String>) = Any()
                @Root
                fun root2(t: java.util.Optional<Int>) = Any()

            }
            """.trimIndent()
        )
        Assertions.assertThat(draw.nodes).hasSize(5)
    }


    @Test
    fun testAllWithOnlyDefault() {
        val draw = compile(
            """
            @KoraApp
            interface ExampleApplication {
                interface TestInterface
                class TestClass : TestInterface

                @Root
                fun root(all: All<TestInterface>) = Any()

                @DefaultComponent
                fun defaultDependency() = TestClass()
            }
            """.trimIndent()
        )
        Assertions.assertThat(draw.nodes).hasSize(2)
        draw.init()
    }

    @Test
    fun testAllWithDefaultAndNonDefault() {
        val draw = compile(
            """
            @KoraApp
            interface ExampleApplication {
                interface TestInterface {}
                class TestClass1 : TestInterface
                class TestClass2 : TestInterface

                @Root
                fun root1(all: All<TestInterface>) = Any()

                @DefaultComponent
                fun defaultDependency() = TestClass1()

                fun nonDefaultDependency() = TestClass2()
            }
            """.trimIndent()
        )
        Assertions.assertThat(draw.nodes).hasSize(2)
        draw.init()
    }

    @Test
    @Disabled
    fun testBugged() {
        val draw = compile(
            """
            @KoraApp
            interface ExampleApplication {
                interface TestInterface {}
                class TestClass1 : TestInterface
                class TestClass2 : TestInterface

                @Root
                fun root1(all: All<TestInterface>) = Any()

                @Root
                fun root2(cl: TestClass1) = Any()

                @DefaultComponent
                fun defaultDependency() = TestClass1()

                fun nonDefaultDependency() = TestClass2()
            }
            """.trimIndent()
        )
        Assertions.assertThat(draw.nodes).hasSize(4)
        draw.init()
    }

    @Test
    fun testGraphDependency() {
        val draw = compile(
            """
            @KoraApp
            interface ExampleApplication {
                @Root
                fun root1(graph: Graph): String { return ""; }
            
                @Root
                fun root2(graph: RefreshableGraph): String { return ""; }
            }
            """
        );
        Assertions.assertThat(draw.nodes).hasSize(2);
        draw.init();
    }

    @Test
    fun testNodeDependency() {
        val draw = compile(
            """
            @KoraApp
            interface ExampleApplication {
                @Root
                fun root1(node: Node<String>): Any { return ""; }

                fun component(): String { return ""; }
            }
            """
        );
        Assertions.assertThat(draw.nodes).hasSize(2);
        draw.init();
    }

    @Test
    fun testUnresolvedDependencyOfTemplateFactoryIsReportedAsDiagnostic() {
        Assertions.assertThatThrownBy {
            compile(
                """
                @KoraApp
                interface ExampleApplication {
                    class Holder<T>(val value: T)
                    class Wrapper<T>(val value: T)

                    fun <T> wrapper(holder: Holder<T>): Wrapper<T> { return Wrapper(holder.value); }

                    @Root
                    fun root(wrapper: Wrapper<String>): Any { return ""; }
                }
                """
            )
        }
            .isInstanceOf(CompilationErrorException::class.java)
            .hasMessageContaining("No component found for dependency")
            .hasMessageContaining("parameter: ")

        val messages = compileResult.assertFailure().messages.joinToString("\n")
        Assertions.assertThat(messages).doesNotContain("NoSuchElementException")
    }

    @Test
    fun testDuplicateDependencyPrintsReadableRequiredAt() {
        Assertions.assertThatThrownBy {
            compile(
                """
                @KoraApp
                interface ExampleApplication {
                    class Class1
                    class Class2<T>

                    @Tag(Class1::class)
                    fun c1() = Class1()

                    @Tag(Class1::class)
                    fun c2() = Class1()

                    @Root
                    fun root(@Tag(Class1::class) class1: Class1, other: Class2<Class1>?): Any { return ""; }
                }
                """
            )
        }
            .isInstanceOf(CompilationErrorException::class.java)
            .hasMessageContaining("Multiple components match dependency")

        val messages = compileResult.assertFailure().messages.joinToString("\n")
        Assertions.assertThat(messages).containsPattern(
            """ExampleApplication#root\(\n\s+@Tag\(ExampleApplication\.Class1::class\) [\w.]+\.ExampleApplication\.Class1,\n\s+ExampleApplication\.Class2<ExampleApplication\.Class1>\?\)"""
        )
        Assertions.assertThat(messages).containsPattern(
            """parameter: @Tag\(ExampleApplication\.Class1::class\) [\w.]+\.ExampleApplication\.Class1 class1"""
        )
    }

    @Test
    fun testValueOfBreaksCycleOfFinalClasses() {
        for (root in listOf("A", "B")) {
            val draw = compile(
                """
                @KoraApp
                interface ExampleApplication {
                    class A(val b: ValueOf<B>)
                    class B(val a: A)

                    ${if (root == "A") "@Root" else ""}
                    fun a(b: ValueOf<B>): A = A(b)
                    ${if (root == "B") "@Root" else ""}
                    fun b(a: A): B = B(a)
                }
                """.trimIndent()
            )
            val graph = draw.init()
            val b = graph.get(draw.nodes.first { it.type().typeName.endsWith("\$B") })!!
            val a = b.javaClass.getMethod("getA").invoke(b)
            val valueOfB = a.javaClass.getMethod("getB").invoke(a) as ValueOf<*>
            Assertions.assertThat(valueOfB.get()).isSameAs(b)
            graph.release()
        }
    }

    @Test
    fun testPromiseOfInsideCycleBreaksCycleOfFinalClasses() {
        for (root in listOf("U", "S")) {
            val draw = compile(
                """
                @KoraApp
                interface ExampleApplication {
                    class T
                    class U(val s: PromiseOf<S>)
                    class S(val u: U, t: T)

                    fun t(): T = T()
                    ${if (root == "U") "@Root" else ""}
                    fun u(s: PromiseOf<S>): U = U(s)
                    ${if (root == "S") "@Root" else ""}
                    fun s(u: U, t: T): S = S(u, t)
                }
                """.trimIndent()
            )
            val graph = draw.init()
            val s = graph.get(draw.nodes.first { it.type().typeName.endsWith("\$S") })!!
            val u = s.javaClass.getMethod("getU").invoke(s)
            val promiseOfS = u.javaClass.getMethod("getS").invoke(u) as PromiseOf<*>
            Assertions.assertThat(promiseOfS.get().orElseThrow()).isSameAs(s)
            graph.release()
        }
    }

    @Test
    fun testPromiseOfCycleTargetIsCreatedForUnconditionalRoot() {
        val draw = compile(
            """
            @KoraApp
            interface ExampleApplication {
                @Tag(io.koraframework.kora.app.ksp.ConditionalComponentTest.FailedCondition::class)
                fun failed(): GraphCondition = io.koraframework.kora.app.ksp.ConditionalComponentTest.FailedCondition()

                class T
                class S(u: U, t: T)
                class U(val s: PromiseOf<S>)
                class R(s: S)

                fun t(): T = T()
                fun s(u: U, t: T): S = S(u, t)
                @Root
                fun u(s: PromiseOf<S>): U = U(s)
                @Root
                @Conditional(tag = io.koraframework.kora.app.ksp.ConditionalComponentTest.FailedCondition::class)
                fun r(s: S): R = R(s)
            }
            """.trimIndent()
        )
        val graph = draw.init()
        val u = graph.get(draw.nodes.first { it.type().typeName.endsWith("\$U") })!!
        val promiseOfS = u.javaClass.getMethod("getS").invoke(u) as PromiseOf<*>
        Assertions.assertThat(promiseOfS.get()).isPresent
    }

    @Test
    fun testValueOfOfSeveralConditionalComponentsDoesNotBreakCycle() {
        // ValueOf<X> resolves to a one-of over x1 and x2, deferring it to the one candidate on the cycle would lose the other
        Assertions.assertThatThrownBy {
            compile(
                """
                @KoraApp
                interface ExampleApplication {
                    @Tag(io.koraframework.kora.app.ksp.ConditionalComponentTest.FailedCondition::class)
                    fun failed(): GraphCondition = io.koraframework.kora.app.ksp.ConditionalComponentTest.FailedCondition()
                    @Tag(io.koraframework.kora.app.ksp.ConditionalComponentTest.MatchesCondition::class)
                    fun matches(): GraphCondition = io.koraframework.kora.app.ksp.ConditionalComponentTest.MatchesCondition()

                    class A(x: ValueOf<X>)
                    class X(a: A)

                    @Root
                    fun a(x: ValueOf<X>): A = A(x)
                    @Conditional(tag = io.koraframework.kora.app.ksp.ConditionalComponentTest.FailedCondition::class)
                    fun x1(a: A): X = X(a)
                    @Conditional(tag = io.koraframework.kora.app.ksp.ConditionalComponentTest.MatchesCondition::class)
                    fun x2(a: A): X = X(a)
                }
                """.trimIndent()
            )
        }
            .isInstanceOf(CompilationErrorException::class.java)
            .hasMessageContaining("Circular dependency found")
    }

    @Test
    fun testWideAllCompilesInLinearTime() {
        val sb = StringBuilder("@KoraApp\ninterface ExampleApplication {\n")
        for (i in 0 until 150) {
            sb.append("    fun c").append(i).append("(): Int = ").append(i).append("\n")
        }
        sb.append("    @Root\n    fun root(all: All<Int>): String = all.count().toString()\n}\n")
        val started = System.nanoTime()
        val draw = compile(sb.toString())
        val took = java.time.Duration.ofNanos(System.nanoTime() - started)
        val graph = draw.init()
        val rootNode = draw.nodes.first { it.type() == String::class.java }
        Assertions.assertThat(graph.get(rootNode)).isEqualTo("150")
        Assertions.assertThat(took).isLessThan(java.time.Duration.ofSeconds(60))
    }
}
