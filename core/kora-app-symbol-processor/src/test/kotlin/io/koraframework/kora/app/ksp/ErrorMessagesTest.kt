package io.koraframework.kora.app.ksp

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.Test

class ErrorMessagesTest : AbstractKoraAppProcessorTest() {

    @Test
    fun circularDependencyShowsWholeCycle() {
        val message = errorMessage(
            """
            @KoraApp
            interface ExampleApplication {
                class Class1
                class Class2
                class Class3

                @Root
                fun class1(value: Class2) = Class1()

                fun class2(value: Class3) = Class2()

                fun class3(value: Class1) = Class3()
            }
            """.trimIndent()
        )

        val app = testPackage() + ".ExampleApplication"
        assertThat(message).isEqualTo(
            """
            Circular dependency found:
              $app.Class1 (no tags)

            Dependency cycle:
              @--- factory  $app#class1(...)
              ^--- factory  $app#class2(...)
              ^--- factory  $app#class3(...)
              ^--- factory  $app#class1(...) [CYCLE]

            Required at:
              $app#class3(
                $app.Class1)
              parameter: $app.Class1 value

            Note:
              Kora can break a cycle with a proxy only for interface or open class dependency, but $app.Class1 is final.

            Fix:
              - Depend on an interface implemented by $app.Class1 instead of the class itself, or make the class open, so Kora can break the cycle with a proxy.
              - Move shared state into a separate component.
              - Do not create dependency cycles in Lifecycle.
            """.trimIndent()
        )
    }

    @Test
    fun cycleThroughOpenClassWithoutNoArgConstructorHasNote() {
        val message = errorMessage(
            """
            @KoraApp
            interface ExampleApplication {
                open class Class1(value: String) {
                    open fun hello() = "hello"
                }
                class Class2(val value: Class1)

                fun string() = ""

                @Root
                fun class1(value: Class2, string: String) = Class1(string)

                fun class2(value: Class1) = Class2(value)
            }
            """.trimIndent()
        )

        val app = testPackage() + ".ExampleApplication"
        assertThat(message)
            .contains("Circular dependency found:")
            .contains("Kora can break a cycle with a proxy of a class only if the class has a non-private constructor callable without arguments, but $app.Class1 has none.")
            .contains("add a non-private constructor callable without arguments to it")
            .doesNotContain("ValueOf")
    }

    @Test
    fun cycleThroughOpenClassWithFinalMembersHasNote() {
        val message = errorMessage(
            """
            @KoraApp
            interface ExampleApplication {
                open class Class1 {
                    fun hello() = "hello"
                    val value: String = "value"
                }
                class Class2(val value: Class1)

                @Root
                fun class1(value: Class2) = Class1()

                fun class2(value: Class1) = Class2(value)
            }
            """.trimIndent()
        )

        val app = testPackage() + ".ExampleApplication"
        assertThat(message)
            .contains("Circular dependency found:")
            .contains("Kora can break a cycle with a proxy of a class only if the proxy can override its members, but $app.Class1 has final members: hello(), value.")
            .contains("or make these members open")
    }

    @Test
    fun cycleThroughAllHasNoLazyWrapperFix() {
        val message = errorMessage(
            """
            @KoraApp
            interface ExampleApplication {
                @Root
                fun root(o: TestInterface): Any = o

                @Tag(Cond1::class)
                fun cond1(all: All<ValueOf<TestInterface>>): GraphCondition = Cond1()
            }
            """.trimIndent(), """
            interface TestInterface
            """.trimIndent(), """
            class Cond1 : GraphCondition {
                override fun eval(): GraphCondition.ConditionResult = GraphCondition.ConditionResult.Matched("cond1")
            }
            """.trimIndent(), """
            @Component
            @Conditional(tag = Cond1::class)
            class TestClass1 : TestInterface
            """.trimIndent()
        )

        assertThat(message)
            .contains("Cycle goes through All<T>, TypeRef<T> or Graph dependency, which cannot be replaced with a proxy.")
            .doesNotContain("All<ValueOf<T>>")
            .doesNotContain("Break the cycle with ValueOf<T>")
    }

    @Test
    fun unresolvedNullableDependencyHasNullabilityNote() {
        val message = errorMessage(
            """
            @KoraApp
            interface ExampleApplication {
                class Class1

                fun class1(): Class1? = null

                @Root
                fun root(value: Class1) = ""
            }
            """.trimIndent()
        )

        val app = testPackage() + ".ExampleApplication"
        assertThat(message).contains(
            """
            Note:
              Found component(s) with the same type but different nullability. Kotlin nullable and non-nullable types are different dependency keys:
              - $app.Class1? (no tags) from factory  $app#class1()

            Fix:
              - Make the dependency and the component types agree on nullability.
            """.trimIndent()
        )
    }

    @Test
    fun unresolvedDependencyHasNoNullabilityNoteWithoutNullableCandidate() {
        val message = errorMessage(
            """
            @KoraApp
            interface ExampleApplication {
                class Class1

                @Root
                fun root(value: Class1) = ""
            }
            """.trimIndent()
        )

        assertThat(message).doesNotContain("nullability")
    }

    @Test
    fun multipleGraphConditionsListCandidates() {
        val message = errorMessage(
            """
            @KoraApp
            interface ExampleApplication {
                class Cond1 : GraphCondition {
                    override fun eval(): GraphCondition.ConditionResult = GraphCondition.ConditionResult.Matched("cond1")
                }

                @Root
                @Conditional(tag = Cond1::class)
                fun root() = ""

                @Tag(Cond1::class)
                fun cond1(): GraphCondition = Cond1()

                @Tag(Cond1::class)
                fun cond2(): GraphCondition = Cond1()
            }
            """.trimIndent()
        )

        val app = testPackage() + ".ExampleApplication"
        assertThat(message).isEqualTo(
            """
            Multiple GraphCondition components match condition tag:
              required condition tag: @Tag($app.Cond1::class)
              component: factory  $app#root()

            Candidates:
              - factory  $app#cond1()
              - factory  $app#cond2()

            Fix:
              - Keep only one GraphCondition for this tag.
              - Use different @Tag(...) values for different conditions.
            """.trimIndent()
        )
    }

    @Test
    fun untaggedDependencySuggestsTagOfFoundComponent() {
        val message = errorMessage(
            """
            @KoraApp
            interface ExampleApplication {
                class TestService

                @Tag(MyTag::class)
                annotation class MyTag

                @Root
                fun root(service: TestService) = ""

                @MyTag
                fun service() = TestService()
            }
            """.trimIndent()
        )

        val app = testPackage() + ".ExampleApplication"
        assertThat(message).contains(
            """
            Note:
              Found component(s) of the same type with other tags. Maybe the tag was forgotten or mixed up:
              - $app.TestService with @$app.MyTag from factory  $app#service()

            Fix:
              - Request the dependency with @$app.MyTag to use the component with this tag.
              - Or remove the tag from the component declaration so it matches this dependency.
              - Add @Component to an implementation of $app.TestService.
            """.trimIndent()
        )
    }

    @Test
    fun taggedDependencySuggestsRemovingTag() {
        val message = errorMessage(
            """
            @KoraApp
            interface ExampleApplication {
                class TestService
                class OtherTag

                @Root
                fun root(@Tag(OtherTag::class) service: TestService) = ""

                fun service() = TestService()
            }
            """.trimIndent()
        )

        val app = testPackage() + ".ExampleApplication"
        assertThat(message)
            .startsWith("No component found for dependency:\n  $app.TestService with @Tag($app.OtherTag::class)")
            .contains("^--- $app.TestService @Tag($app.OtherTag::class) [MISSING]")
            .contains(
                """
                  - $app.TestService (no tags) from factory  $app#service()

                Fix:
                  - Remove @Tag($app.OtherTag::class) from the dependency to use the component without tags.
                  - Or add @Tag($app.OtherTag::class) to the component declaration so it matches this dependency.
                """.trimIndent()
            )
    }

    private fun errorMessage(vararg sources: String): String {
        assertThat(catchThrowable { compile(*sources) }).isNotNull()
        return compileResult.assertFailure().messages.first()
    }
}
