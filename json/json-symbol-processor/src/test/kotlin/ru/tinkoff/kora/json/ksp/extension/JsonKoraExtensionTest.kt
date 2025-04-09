package ru.tinkoff.kora.json.ksp.extension

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.tinkoff.kora.application.graph.ApplicationGraphDraw
import ru.tinkoff.kora.json.ksp.JsonSymbolProcessorProvider
import ru.tinkoff.kora.kora.app.ksp.KoraAppProcessorProvider
import ru.tinkoff.kora.ksp.common.AbstractSymbolProcessorTest

class JsonKoraExtensionTest : AbstractSymbolProcessorTest() {
    @Test
    fun testReaderFromAnnotatedClass() {
        compile0(listOf(KoraAppProcessorProvider(), JsonSymbolProcessorProvider()), """
            @KoraApp
            interface TestApp {
                @ru.tinkoff.kora.json.common.annotation.Json
                data class TestClass(val a: String)

                @Root
                fun test(r: ru.tinkoff.kora.json.common.JsonReader<TestClass>) {}
            }
        """.trimIndent())

        compileResult.assertSuccess()
        val graph = newObject("TestAppGraph").invoke<ApplicationGraphDraw>("graph")!!
        assertThat(graph.nodes).hasSize(2)
    }

    @Test
    fun testReaderFromNonAnnotatedClass() {
        compile0(listOf(KoraAppProcessorProvider(), JsonSymbolProcessorProvider()), """
            @KoraApp
            interface TestApp  : ru.tinkoff.kora.json.common.JsonCommonModule{
                data class TestClass(val a: String)

                @Root
                fun test(r: ru.tinkoff.kora.json.common.JsonReader<ru.tinkoff.kora.json.ksp.dto.JavaBeanDto>) {}
            }
        """.trimIndent())

        compileResult.assertSuccess()
        val graph = newObject("TestAppGraph").invoke<ApplicationGraphDraw>("graph")!!
        assertThat(graph.nodes).hasSize(2)
    }

    @Test
    fun testWriterFromAnnotatedClass() {
        compile0(listOf(KoraAppProcessorProvider(), JsonSymbolProcessorProvider()), """
            @KoraApp
            interface TestApp {
                @ru.tinkoff.kora.json.common.annotation.Json
                data class TestClass(val a: String)

                @Root
                fun test(r: ru.tinkoff.kora.json.common.JsonWriter<TestClass>) {}
            }
        """.trimIndent())

        compileResult.assertSuccess()
        val graph = newObject("TestAppGraph").invoke<ApplicationGraphDraw>("graph")!!
        assertThat(graph.nodes).hasSize(2)
    }

    @Test
    fun testWriterFromNonAnnotatedClass() {
        compile0(listOf(KoraAppProcessorProvider(), JsonSymbolProcessorProvider()), """
            @KoraApp
            interface TestApp {
                data class TestClass(val a: String)

                @Root
                fun test(r: ru.tinkoff.kora.json.common.JsonWriter<TestClass>) {}
            }
        """.trimIndent())

        compileResult.assertSuccess()
        val graph = newObject("TestAppGraph").invoke<ApplicationGraphDraw>("graph")!!
        assertThat(graph.nodes).hasSize(2)
    }

    @Test
    fun testReaderFromAnnotatedEnum() {
        compile0(listOf(KoraAppProcessorProvider(), JsonSymbolProcessorProvider()), """
            @KoraApp
            interface TestApp : ru.tinkoff.kora.json.common.JsonCommonModule {
                @ru.tinkoff.kora.json.common.annotation.Json
                enum class TestEnum { INSTANCE }

                @Root
                fun test(r: ru.tinkoff.kora.json.common.JsonReader<TestEnum>) {}
            }
        """.trimIndent())

        compileResult.assertSuccess()
        val graph = newObject("TestAppGraph").invoke<ApplicationGraphDraw>("graph")!!
        assertThat(graph.nodes).hasSize(3)
    }

    @Test
    fun testReaderFromNonAnnotatedEnum() {
        compile0(listOf(KoraAppProcessorProvider(), JsonSymbolProcessorProvider()), """
            @KoraApp
            interface TestApp : ru.tinkoff.kora.json.common.JsonCommonModule {
                enum class TestEnum { INSTANCE }

                @Root
                fun test(r: ru.tinkoff.kora.json.common.JsonReader<TestEnum>) {}
            }
        """.trimIndent())

        compileResult.assertSuccess()
        val graph = newObject("TestAppGraph").invoke<ApplicationGraphDraw>("graph")!!
        assertThat(graph.nodes).hasSize(3)
    }

    @Test
    fun testWriterFromAnnotatedEnum() {
        compile0(listOf(KoraAppProcessorProvider(), JsonSymbolProcessorProvider()), """
            @KoraApp
            interface TestApp : ru.tinkoff.kora.json.common.JsonCommonModule {
                @ru.tinkoff.kora.json.common.annotation.Json
                enum class TestEnum { INSTANCE }

                @Root
                fun test(r: ru.tinkoff.kora.json.common.JsonWriter<TestEnum>) {}
            }
        """.trimIndent())

        compileResult.assertSuccess()
        val graph = newObject("TestAppGraph").invoke<ApplicationGraphDraw>("graph")!!
        assertThat(graph.nodes).hasSize(3)
    }

    @Test
    fun testWriterFromNonAnnotatedEnum() {
        compile0(listOf(KoraAppProcessorProvider(), JsonSymbolProcessorProvider()), """
            @KoraApp
            interface TestApp : ru.tinkoff.kora.json.common.JsonCommonModule {
                enum class TestEnum { INSTANCE }

                @Root
                fun test(r: ru.tinkoff.kora.json.common.JsonWriter<TestEnum>) {}
            }
        """.trimIndent())

        compileResult.assertSuccess()
        val graph = newObject("TestAppGraph").invoke<ApplicationGraphDraw>("graph")!!
        assertThat(graph.nodes).hasSize(3)
    }
}
