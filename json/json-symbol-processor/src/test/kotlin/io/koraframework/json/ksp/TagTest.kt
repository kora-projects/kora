package io.koraframework.json.ksp

import io.koraframework.common.annotation.Tag
import io.koraframework.json.common.JsonReader
import io.koraframework.json.common.JsonWriter
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TagTest : AbstractJsonSymbolProcessorTest() {
    @Test
    fun testTagWriterMapping() {
        compile("""
            @JsonWriter
            data class TestClass(@Tag(TestClass::class) val testField: String)
        """.trimIndent())

        val constructor = writerClass("TestClass").constructors[0]
        assertThat(constructor.parameterTypes).containsExactly(JsonWriter::class.java)
        assertThat(constructor.parameters[0].getAnnotation(Tag::class.java).value.java)
            .isEqualTo(loadClass("TestClass"))

        writer("TestClass", JsonWriter<String> { gen, _ -> gen.writeString("from tagged writer") })
            .assertWrite(new("TestClass", "test"), """{"testField":"from tagged writer"}""")
    }

    @Test
    fun testTagReaderMapping() {
        compile("""
            @JsonReader
            data class TestClass(@Tag(TestClass::class) val testField: String)
        """.trimIndent())

        val constructor = readerClass("TestClass").constructors[0]
        assertThat(constructor.parameterTypes).containsExactly(JsonReader::class.java)
        assertThat(constructor.parameters[0].getAnnotation(Tag::class.java).value.java)
            .isEqualTo(loadClass("TestClass"))

        reader("TestClass", JsonReader<String> { p -> p.skipChildren(); "from tagged reader" })
            .assertRead("""{"testField":"x"}""", new("TestClass", "from tagged reader"))
    }

    @Test
    fun testTagNullableMapping() {
        compile("""
            @Json
            data class TestClass(@Tag(TestClass::class) val testField: String?)
        """.trimIndent())

        reader("TestClass", JsonReader<String> { p -> p.text })
            .assertRead("""{"testField":"x"}""", new("TestClass", "x"))
        writer("TestClass", JsonWriter<String> { gen, v -> gen.writeString(v + "!") })
            .assertWrite(new("TestClass", "x"), """{"testField":"x!"}""")
    }
}
