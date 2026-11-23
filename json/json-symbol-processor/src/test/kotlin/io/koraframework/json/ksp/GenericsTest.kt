package io.koraframework.json.ksp

import io.koraframework.json.common.JsonWriter
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import tools.jackson.core.exc.StreamReadException
import io.koraframework.json.common.JsonReader
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

    @Test
    fun testGenericWriterSkipsNullForNullableTypeArgument() {
        compile(
            """
            @Json
            data class TestClass <T> (val value: T)
            """.trimIndent(),
            """
                @KoraApp
                interface TestApp : io.koraframework.json.common.JsonModule {
                  @Root
                  fun root(w: io.koraframework.json.common.JsonWriter<TestClass<String?>>, r: io.koraframework.json.common.JsonReader<TestClass<Int>>) = ""
                }
            """.trimIndent()
        )
        val graph = loadClass("TestAppGraph").toGraph()
        val writer = graph.findAllByType(writerClass("TestClass")) as List<JsonWriter<Any?>>
        val reader = graph.findAllByType(readerClass("TestClass")) as List<JsonReader<Any?>>

        writer[0].assertWrite(new("TestClass", null), "{}")
        writer[0].assertWrite(new("TestClass", "test"), "{\"value\":\"test\"}")

        // a field typed by a bare type parameter stays required on read
        reader[0].assertRead("{\"value\":42}", new("TestClass", 42))
        Assertions.assertThatThrownBy { reader[0].read("{}") }.isInstanceOf(StreamReadException::class.java)
        Assertions.assertThatThrownBy { reader[0].read("{\"value\":null}") }.isInstanceOf(StreamReadException::class.java)
    }

}
