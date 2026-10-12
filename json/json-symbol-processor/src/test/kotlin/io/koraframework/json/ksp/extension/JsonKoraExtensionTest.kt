package io.koraframework.json.ksp.extension

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import io.koraframework.application.graph.ApplicationGraphDraw
import io.koraframework.json.common.JsonReader
import io.koraframework.json.common.JsonWriter
import io.koraframework.ksp.common.GraphUtil.toGraph
import org.intellij.lang.annotations.Language
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
                fun test(r: io.koraframework.json.common.JsonReader<TestClass>): Any = r
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
                fun test(r: io.koraframework.json.common.JsonWriter<TestClass>): Any = r
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
                fun test(r: io.koraframework.json.common.JsonReader<TestEnum>): Any = r
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
                fun test(r: io.koraframework.json.common.JsonWriter<TestEnum>): Any = r
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
                fun test(r: io.koraframework.json.common.JsonReader<TestInterface>): Any = r
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
                fun test(r: io.koraframework.json.common.JsonWriter<TestInterface>): Any = r
            }
        """.trimIndent()
        )

        compileResult.assertSuccess()
        val graph = newObject("TestAppGraph").invoke<ApplicationGraphDraw>("graph")!!
        assertThat(graph.nodes).hasSize(4)
    }

    @Test
    fun testDelegatingValueTypeAsField() {
        compileCodecApp(
            """
            class UserId(val id: Long) {
                @io.koraframework.json.common.annotation.JsonWriter
                fun toJson(): Long = id

                companion object {
                    @io.koraframework.json.common.annotation.JsonReader
                    fun of(value: Long): UserId = UserId(value)
                }
            }

            @io.koraframework.json.common.annotation.Json
            data class User(val id: UserId, val friends: List<UserId>)
            """, "User"
        )

        assertThat(roundTrip("{\"id\":1,\"friends\":[2]}")).isEqualTo("{\"id\":1,\"friends\":[2]}")
    }

    @Test
    fun testDelegatingValueTypeAsRoot() {
        compileCodecApp(
            """
            class UserId(val id: Long) {
                @io.koraframework.json.common.annotation.JsonWriter
                fun toJson(): Long = id

                companion object {
                    @io.koraframework.json.common.annotation.JsonReader
                    fun of(value: Long): UserId = UserId(value)
                }
            }
            """, "UserId"
        )

        assertThat(roundTrip("42")).isEqualTo("42")
    }

    @Test
    fun testDelegatingValueClassAsField() {
        compileCodecApp(
            """
            @JvmInline
            value class UserId(val id: Long) {
                @io.koraframework.json.common.annotation.JsonWriter
                fun toJson(): Long = id

                companion object {
                    @io.koraframework.json.common.annotation.JsonReader
                    fun of(value: Long): UserId = UserId(value)
                }
            }

            @io.koraframework.json.common.annotation.Json
            data class User(val id: UserId, val friends: List<UserId>, val boss: UserId?)
            """, "User"
        )

        assertThat(roundTrip("{\"id\":1,\"friends\":[2],\"boss\":3}")).isEqualTo("{\"id\":1,\"friends\":[2],\"boss\":3}")
    }

    private fun compileCodecApp(@Language("kotlin") types: String, codecType: String) {
        compile0(
            listOf(KoraAppProcessorProvider(), JsonSymbolProcessorProvider()),
            types.trimIndent(),
            """
            @KoraApp
            interface TestApp : io.koraframework.json.common.JsonModule {
                class Codec(val r: io.koraframework.json.common.JsonReader<*>, val w: io.koraframework.json.common.JsonWriter<*>)

                @Root
                fun codec(r: io.koraframework.json.common.JsonReader<$codecType>, w: io.koraframework.json.common.JsonWriter<$codecType>) = Codec(r, w)
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()
    }

    @Suppress("UNCHECKED_CAST")
    private fun roundTrip(json: String): String {
        val codecClass = loadClass("TestApp\$Codec")
        val codec = loadClass("TestAppGraph").toGraph().findByType(codecClass)!!
        val reader = codecClass.getMethod("getR").invoke(codec) as JsonReader<Any?>
        val writer = codecClass.getMethod("getW").invoke(codec) as JsonWriter<Any?>
        return String(writer.toByteArray(reader.read(json.toByteArray())))
    }
}
