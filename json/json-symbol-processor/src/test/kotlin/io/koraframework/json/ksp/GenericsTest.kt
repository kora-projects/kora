package io.koraframework.json.ksp

import org.junit.jupiter.api.Test
import io.koraframework.json.common.JsonReader
import io.koraframework.json.common.JsonWriter
import io.koraframework.ksp.common.GraphUtil.toGraph

class GenericsTest : AbstractJsonSymbolProcessorTest() {
    @Test
    fun testGenericJsonReaderExtension() {
        compile(
            """
            @Json
            data class TestClass <T> (val value:T)             
            """.trimIndent(),
            """
                @KoraApp
                interface TestApp : io.koraframework.json.common.JsonModule {
                  @Root
                  fun root(w1: io.koraframework.json.common.JsonReader<TestClass<String>>, w2: io.koraframework.json.common.JsonReader<TestClass<Int>>, w3: io.koraframework.json.common.JsonReader<TestClass<Int?>>) = ""
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
            """
            @Json
            data class TestClass <T> (val value:T)             
            """.trimIndent(),
            """
                @KoraApp
                interface TestApp : io.koraframework.json.common.JsonModule {
                  @Root
                  fun root(w1: io.koraframework.json.common.JsonWriter<TestClass<String>>, w2: io.koraframework.json.common.JsonWriter<TestClass<Int>>, w3: io.koraframework.json.common.JsonWriter<TestClass<Int?>>) = ""
                }
            """.trimIndent()
        )
        val graph = loadClass("TestAppGraph").toGraph()
        val writer = graph.findAllByType(writerClass("TestClass")) as List<JsonWriter<Any?>>

        writer[0].assertWrite(new("TestClass", "test"), "{\"value\":\"test\"}")
        writer[1].assertWrite(new("TestClass", 42), "{\"value\":42}")
    }

    @Test
    fun testGenericJsonWriterExtensionWithIncludeClassAlways() {
        compile(
            """
            import io.koraframework.json.common.annotation.JsonInclude
            
            @JsonInclude(JsonInclude.IncludeType.ALWAYS)
            @Json
            data class TestClass <T> (val value: T?, val values: List<T>?)             
            """.trimIndent(),
            """
                @KoraApp
                interface TestApp : io.koraframework.json.common.JsonModule {
                  @Root
                  fun root(w1: io.koraframework.json.common.JsonWriter<TestClass<String?>>) = ""
                }
            """.trimIndent()
        )
        val graph = loadClass("TestAppGraph").toGraph()
        val writer = graph.findAllByType(writerClass("TestClass")) as List<JsonWriter<Any?>>

        writer[0].assertWrite(new("TestClass", null, null), "{\"value\":null,\"values\":null}")
    }

    @Test
    fun testGenericJsonReaderExtensionWithAnnotation() {
        compile(
            """
            @io.koraframework.json.common.annotation.Json
            data class TestClass <T> (val value: T)             
            """.trimIndent(),
            """
                @KoraApp
                interface TestApp : io.koraframework.json.common.JsonModule {
                  @Root
                  fun root(w1: io.koraframework.json.common.JsonReader<TestClass<String>>, w2: io.koraframework.json.common.JsonReader<TestClass<Int>>) = ""
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
            """
            @io.koraframework.json.common.annotation.Json
            data class TestClass <T> (val value:T)             
            """.trimIndent(),
            """
                @KoraApp
                interface TestApp : io.koraframework.json.common.JsonModule {
                  @Root
                  fun root(w1: io.koraframework.json.common.JsonWriter<TestClass<String>>, w2: io.koraframework.json.common.JsonWriter<TestClass<Int>>) = ""
                }
            """.trimIndent()
        )
        val graph = loadClass("TestAppGraph").toGraph()
        val writer = graph.findAllByType(writerClass("TestClass")) as List<JsonWriter<Any?>>

        writer[0].assertWrite(new("TestClass", "test"), "{\"value\":\"test\"}")
        writer[1].assertWrite(new("TestClass", 42), "{\"value\":42}")
    }

}
