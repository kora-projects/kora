package io.koraframework.json.ksp

import io.koraframework.json.common.JsonNullable
import io.koraframework.json.common.JsonReader
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import tools.jackson.core.exc.StreamReadException

class MappingTest : AbstractJsonSymbolProcessorTest() {

    @Test
    fun testTaggedReader() {
        compile(
            """
            @JsonReader
            data class TestRecord(@Tag(TestRecord::class) val testField: String)
            """.trimIndent()
        )

        val reader = reader("TestRecord", JsonReader { "from tagged reader" })

        assertThat(reader.read("""{"testField":"test"}""")).isEqualTo(new("TestRecord", "from tagged reader"))
    }

    @Test
    fun testReaderMappingWithConstructorDependency() {
        allWarningsAsErrors = true
        compile(
            """
            class FieldReader(private val radix: Int) : io.koraframework.json.common.JsonReader<Int> {
                override fun read(parser: tools.jackson.core.JsonParser): Int = parser.valueAsString.toInt(radix)
            }

            @JsonReader
            data class TestRecord(@Mapping(FieldReader::class) val testField: Int)
            """.trimIndent()
        )

        val fieldReader = loadClass("FieldReader").constructors[0].newInstance(16)
        val reader = reader("TestRecord", fieldReader)

        assertThat(reader.read("""{"testField":"ff"}""")).isEqualTo(new("TestRecord", 255))
    }

    @Test
    fun testWriterMappingWithConstructorDependency() {
        compile(
            """
            class FieldWriter(private val radix: Int) : io.koraframework.json.common.JsonWriter<Int> {
                override fun write(gen: tools.jackson.core.JsonGenerator, value: Int?) {
                    gen.writeString(value!!.toString(radix))
                }
            }

            @JsonWriter
            data class TestRecord(@Mapping(FieldWriter::class) val testField: Int)
            """.trimIndent()
        )

        val fieldWriter = loadClass("FieldWriter").constructors[0].newInstance(16)
        val writer = writer("TestRecord", fieldWriter)

        assertThat(writer.toString(new("TestRecord", 255))).isEqualTo("""{"testField":"ff"}""")
    }

    @Test
    fun testReaderMappingReturningNullable() {
        compile(
            """
            class FieldReader : io.koraframework.json.common.JsonReader<Int> {
                override fun read(parser: tools.jackson.core.JsonParser): Int? = if (parser.string == "none") null else parser.string.toInt(16)
            }

            @JsonReader
            data class TestRecord(@Mapping(FieldReader::class) val testField: Int)
            """.trimIndent()
        )

        val reader = reader("TestRecord")

        assertThat(reader.read("""{"testField":"ff"}""")).isEqualTo(new("TestRecord", 255))
        assertThatThrownBy { reader.read("""{"testField":"none"}""") }
            .isInstanceOf(StreamReadException::class.java)
            .hasMessageContaining("required field must not be null")
    }

    @Test
    fun testJsonNullableReaderMapping() {
        compile(
            """
            class FieldReader : io.koraframework.json.common.JsonReader<JsonNullable<String>> {
                override fun read(parser: tools.jackson.core.JsonParser): JsonNullable<String> =
                    if (parser.currentToken() == tools.jackson.core.JsonToken.VALUE_NULL) JsonNullable.nullValue() else JsonNullable.of(parser.string)
            }

            @JsonReader
            data class TestRecord(@Mapping(FieldReader::class) val testField: JsonNullable<String>, val other: String)
            """.trimIndent()
        )

        val reader = reader("TestRecord")

        assertThat(reader.read("""{"other":"o"}""")).isEqualTo(new("TestRecord", JsonNullable.undefined<String>(), "o"))
        assertThat(reader.read("""{"testField":null,"other":"o"}""")).isEqualTo(new("TestRecord", JsonNullable.nullValue<String>(), "o"))
        assertThat(reader.read("""{"testField":"v","other":"o"}""")).isEqualTo(new("TestRecord", JsonNullable.of("v"), "o"))
    }

    @Test
    fun testJsonNullableReaderMappingWithoutRequiredFields() {
        compile(
            """
            class FieldReader : io.koraframework.json.common.JsonReader<JsonNullable<String>> {
                override fun read(parser: tools.jackson.core.JsonParser): JsonNullable<String> =
                    if (parser.currentToken() == tools.jackson.core.JsonToken.VALUE_NULL) JsonNullable.nullValue() else JsonNullable.of(parser.string)
            }

            @JsonReader
            data class TestRecord(@Mapping(FieldReader::class) val testField: JsonNullable<String>)
            """.trimIndent()
        )

        val reader = reader("TestRecord")

        assertThat(reader.read("{}")).isEqualTo(new("TestRecord", JsonNullable.undefined<String>()))
        assertThat(reader.read("""{"testField":null}""")).isEqualTo(new("TestRecord", JsonNullable.nullValue<String>()))
    }
}
