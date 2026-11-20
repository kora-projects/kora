package io.koraframework.kora.app.ksp

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PromisedProxyTest : AbstractKoraAppProcessorTest() {

    @Test
    fun proxyOverridesMutableProperty() {
        compile(
            """
            @KoraApp
            interface ExampleApplication {
                open class Class1 {
                    open var value: String = "value"
                    open fun hello() = "hello"
                }
                class Class2(val value: Class1)

                @Root
                fun class1(value: Class2) = Class1()

                fun class2(value: Class1) = Class2(value)
            }
            """.trimIndent()
        ).init()

        assertThat(proxyMethods("\$ExampleApplication_Class1_PromisedProxy")).contains("getValue", "setValue", "hello")
    }

    @Test
    fun proxyOverridesGenericAndVarargFunctions() {
        compile(
            """
            @KoraApp
            interface ExampleApplication {
                interface Interface1 {
                    fun <T> pick(first: T, second: T?): T?
                    fun count(vararg values: String): Int
                    val nullable: String?
                }
                class Impl : Interface1 {
                    override fun <T> pick(first: T, second: T?) = second
                    override fun count(vararg values: String) = values.size
                    override val nullable: String? = null
                }
                class Class2(val value: Interface1)

                @Root
                fun interface1(value: Class2): Interface1 = Impl()

                fun class2(value: Interface1) = Class2(value)
            }
            """.trimIndent()
        ).init()

        assertThat(proxyMethods("\$ExampleApplication_Interface1_PromisedProxy")).contains("pick", "count", "getNullable")
    }

    @Test
    fun proxyOverridesMembersOfGenericInterface() {
        compile(
            """
            @KoraApp
            interface ExampleApplication {
                interface Box<T> {
                    val value: T
                    fun <R> map(mapper: (T) -> R): R
                }
                class Impl : Box<String> {
                    override val value = "value"
                    override fun <R> map(mapper: (String) -> R) = mapper(value)
                }
                class Class2(val value: Box<String>)

                @Root
                fun box(value: Class2): Box<String> = Impl()

                fun class2(value: Box<String>) = Class2(value)
            }
            """.trimIndent()
        ).init()
    }

    private fun proxyMethods(proxyClassName: String) = loadClass(proxyClassName).declaredMethods.filter { !it.isBridge }.map { it.name }
}
