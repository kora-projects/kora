package ru.tinkoff.kora.json.ksp

import org.junit.jupiter.api.Test
import ru.tinkoff.kora.json.common.JsonReader
import ru.tinkoff.kora.json.common.JsonWriter
import ru.tinkoff.kora.kora.app.ksp.KoraAppProcessorProvider
import ru.tinkoff.kora.ksp.common.GraphUtil.toGraph

class GenericsTest : AbstractJsonSymbolProcessorTest() {
    @Test
    fun testGenericJsonReaderExtension() {
        compile(
            listOf(KoraAppProcessorProvider()),
            """
            data class TestClass <T> (val value:T)             
            """.trimIndent(),
            """
                @KoraApp
                interface TestApp : ru.tinkoff.kora.json.common.JsonCommonModule {
                  @Root
                  fun root(w1: ru.tinkoff.kora.json.common.JsonReader<TestClass<String>>, w2: ru.tinkoff.kora.json.common.JsonReader<TestClass<Int>>, w3: ru.tinkoff.kora.json.common.JsonReader<TestClass<Int?>>) = ""
                }
            """.trimIndent()
        )
        val graph = loadClass("TestAppGraph").toGraph()
        val reader = graph.findAllByType(readerClass("TestClass")) as List<JsonReader<Any?>>

        reader[0].assertRead("{\"value\":\"test\"}", new("TestClass", "test"))
        reader[1].assertRead("{\"value\":42}", new("TestClass", 42))
    }

    @Test
    fun testGenericJsonWriterExtension() {
        compile(
            listOf(KoraAppProcessorProvider()),
            """
            data class TestClass <T> (val value:T)             
            """.trimIndent(),
            """
                @KoraApp
                interface TestApp : ru.tinkoff.kora.json.common.JsonCommonModule {
                  @Root
                  fun root(w1: ru.tinkoff.kora.json.common.JsonWriter<TestClass<String>>, w2: ru.tinkoff.kora.json.common.JsonWriter<TestClass<Int>>, w3: ru.tinkoff.kora.json.common.JsonWriter<TestClass<Int?>>) = ""
                }
            """.trimIndent()
        )
        val graph = loadClass("TestAppGraph").toGraph()
        val writer = graph.findAllByType(writerClass("TestClass")) as List<JsonWriter<Any?>>

        writer[0].assertWrite(new("TestClass", "test"), "{\"value\":\"test\"}")
        writer[1].assertWrite(new("TestClass", 42), "{\"value\":42}")
    }

    @Test
    fun testGenericJsonReaderExtensionWithAnnotation() {
        compile(
            listOf(KoraAppProcessorProvider(), JsonSymbolProcessorProvider()),
            """
            @ru.tinkoff.kora.json.common.annotation.Json
            data class TestClass <T> (val value:T)             
            """.trimIndent(),
            """
                @KoraApp
                interface TestApp : ru.tinkoff.kora.json.common.JsonCommonModule {
                  @Root
                  fun root(w1: ru.tinkoff.kora.json.common.JsonReader<TestClass<String>>, w2: ru.tinkoff.kora.json.common.JsonReader<TestClass<Int>>) = ""
                }
            """.trimIndent()
        )
        val graph = loadClass("TestAppGraph").toGraph()
        val reader = graph.findAllByType(readerClass("TestClass")) as List<JsonReader<Any?>>

        reader[0].assertRead("{\"value\":\"test\"}", new("TestClass", "test"))
        reader[1].assertRead("{\"value\":42}", new("TestClass", 42))
    }

    @Test
    fun testGenericJsonWriterExtensionWithAnnotation() {
        compile(
            listOf(KoraAppProcessorProvider(), JsonSymbolProcessorProvider()),
            """
            @ru.tinkoff.kora.json.common.annotation.Json
            data class TestClass <T> (val value:T)             
            """.trimIndent(),
            """
                @KoraApp
                interface TestApp : ru.tinkoff.kora.json.common.JsonCommonModule {
                  @Root
                  fun root(w1: ru.tinkoff.kora.json.common.JsonWriter<TestClass<String>>, w2: ru.tinkoff.kora.json.common.JsonWriter<TestClass<Int>>) = ""
                }
            """.trimIndent()
        )
        val graph = loadClass("TestAppGraph").toGraph()
        val writer = graph.findAllByType(writerClass("TestClass")) as List<JsonWriter<Any?>>

        writer[0].assertWrite(new("TestClass", "test"), "{\"value\":\"test\"}")
        writer[1].assertWrite(new("TestClass", 42), "{\"value\":42}")
    }

}
