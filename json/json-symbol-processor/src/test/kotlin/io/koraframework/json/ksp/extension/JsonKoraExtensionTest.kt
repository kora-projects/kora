package io.koraframework.json.ksp.extension

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import io.koraframework.application.graph.ApplicationGraphDraw
import io.koraframework.json.ksp.JsonSymbolProcessorProvider
import io.koraframework.kora.app.ksp.KoraAppProcessorProvider
import io.koraframework.ksp.common.AbstractSymbolProcessorTest

class JsonKoraExtensionTest : AbstractSymbolProcessorTest() {
    @Test
    fun testReaderFromAnnotatedClass() {
        compile0(
            listOf(KoraAppProcessorProvider(), JsonSymbolProcessorProvider()), """
            @KoraApp
            interface TestApp {
                @io.koraframework.json.common.annotation.Json
                data class TestClass(val a: String)

                @Root
                fun test(r: io.koraframework.json.common.JsonReader<TestClass>) {}
            }
        """.trimIndent()
        )

        compileResult.assertSuccess()
        val graph = newObject("TestAppGraph").invoke<ApplicationGraphDraw>("graph")!!
        assertThat(graph.nodes).hasSize(2)
    }

    @Test
    fun testWriterFromAnnotatedClass() {
        compile0(
            listOf(KoraAppProcessorProvider(), JsonSymbolProcessorProvider()), """
            @KoraApp
            interface TestApp {
                @io.koraframework.json.common.annotation.Json
                data class TestClass(val a: String)

                @Root
                fun test(r: io.koraframework.json.common.JsonWriter<TestClass>) {}
            }
        """.trimIndent()
        )

        compileResult.assertSuccess()
        val graph = newObject("TestAppGraph").invoke<ApplicationGraphDraw>("graph")!!
        assertThat(graph.nodes).hasSize(2)
    }

    @Test
    fun testReaderFromAnnotatedEnum() {
        compile0(
            listOf(KoraAppProcessorProvider(), JsonSymbolProcessorProvider()), """
            @KoraApp
            interface TestApp : io.koraframework.json.common.JsonModule {
                @io.koraframework.json.common.annotation.Json
                enum class TestEnum { INSTANCE }

                @Root
                fun test(r: io.koraframework.json.common.JsonReader<TestEnum>) {}
            }
        """.trimIndent()
        )

        compileResult.assertSuccess()
        val graph = newObject("TestAppGraph").invoke<ApplicationGraphDraw>("graph")!!
        assertThat(graph.nodes).hasSize(3)
    }

    @Test
    fun testWriterFromAnnotatedEnum() {
        compile0(
            listOf(KoraAppProcessorProvider(), JsonSymbolProcessorProvider()), """
            @KoraApp
            interface TestApp : io.koraframework.json.common.JsonModule {
                @io.koraframework.json.common.annotation.Json
                enum class TestEnum { INSTANCE }

                @Root
                fun test(r: io.koraframework.json.common.JsonWriter<TestEnum>) {}
            }
        """.trimIndent()
        )

        compileResult.assertSuccess()
        val graph = newObject("TestAppGraph").invoke<ApplicationGraphDraw>("graph")!!
        assertThat(graph.nodes).hasSize(3)
    }

    @Test
    fun testReaderFromExtensionGeneratedForSealedInterface() {
        compile0(
            listOf(KoraAppProcessorProvider(), JsonSymbolProcessorProvider()), """
            @KoraApp
            interface TestApp {
            
                @io.koraframework.json.common.annotation.JsonDiscriminatorField("type")
                @io.koraframework.json.common.annotation.Json
                sealed interface TestInterface {
                    @io.koraframework.json.common.annotation.Json
                    data class Impl1(val value: String) : TestInterface
                    @io.koraframework.json.common.annotation.Json
                    data class Impl2(val value: Int) : TestInterface
                }

                @Root
                fun test(r: io.koraframework.json.common.JsonReader<TestInterface>) {}
            }
        """.trimIndent()
        )

        compileResult.assertSuccess()
        val graph = newObject("TestAppGraph").invoke<ApplicationGraphDraw>("graph")!!
        assertThat(graph.nodes).hasSize(4)
    }

    @Test
    fun testWriterFromExtensionGeneratedForSealedInterface() {
        compile0(
            listOf(KoraAppProcessorProvider(), JsonSymbolProcessorProvider()), """
            @KoraApp
            interface TestApp {
            
                @io.koraframework.json.common.annotation.JsonDiscriminatorField("type")
                @io.koraframework.json.common.annotation.Json
                sealed interface TestInterface {
                    @io.koraframework.json.common.annotation.Json
                    data class Impl1(val value: String) : TestInterface
                    @io.koraframework.json.common.annotation.Json
                    data class Impl2(val value: Int) : TestInterface
                }

                @Root
                fun test(r: io.koraframework.json.common.JsonWriter<TestInterface>) {}
            }
        """.trimIndent()
        )

        compileResult.assertSuccess()
        val graph = newObject("TestAppGraph").invoke<ApplicationGraphDraw>("graph")!!
        assertThat(graph.nodes).hasSize(4)
    }
}
