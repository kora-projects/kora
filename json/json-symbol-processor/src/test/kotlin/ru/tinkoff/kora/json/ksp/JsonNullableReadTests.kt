package ru.tinkoff.kora.json.ksp

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.tinkoff.kora.json.common.JsonNullable
import ru.tinkoff.kora.json.common.JsonReader
import ru.tinkoff.kora.json.common.JsonValue
import java.io.IOException
import java.sql.Timestamp
import java.time.Instant

class JsonNullableReadTests : AbstractJsonSymbolProcessorTest() {

    @Test
    @Throws(IOException::class)
    fun jsonReaderNativeNullableIsUndefined() {
        compile(
            """
            @JsonReader
            data class TestRecord(@field:JsonField("test_field") val testField: JsonNullable<String>)
            """.trimIndent()
        )

        val o = reader("TestRecord").read(
            """
             {}
             """.trimIndent()
        )!!

        assertThat(o).isEqualTo(new("TestRecord", JsonValue.undefined<Any>()))
    }

    @Test
    @Throws(IOException::class)
    fun jsonReaderNativeNullableIsNullable() {
        compile(
            """
            @JsonReader
            data class TestRecord(@field:JsonField("test_field") val testField: JsonNullable<String>)
            
            """.trimIndent()
        )

        val o = reader("TestRecord").read(
            """
             {"test_field":null}
             """.trimIndent()
        )!!

        assertThat(o).isEqualTo(new("TestRecord", JsonNullable.nullValue<Any>()))
    }

    @Test
    @Throws(IOException::class)
    fun jsonReaderNativeNullableIsPresent() {
        compile(
            """
            @JsonReader
            data class TestRecord(@field:JsonField("test_field") val testField: JsonNullable<String>)
            
            """.trimIndent()
        )

        val o = reader("TestRecord").read(
            """
             {"test_field":"test"}
             """.trimIndent()
        )!!

        assertThat(o).isEqualTo(new("TestRecord", JsonNullable.of("test")))
    }

    @Test
    @Throws(IOException::class)
    fun jsonReaderUserNullableIsUndefined() {
        compile(
            """
            @JsonReader
            data class TestRecord(@field:JsonField("test_field") val testField: JsonNullable<java.sql.Timestamp>)
            """.trimIndent()
        )

        val timestampReader = JsonReader { parser -> Timestamp.from(Instant.ofEpochMilli(parser.longValue)) }

        val o = reader("TestRecord", timestampReader).read(
            """
             {}
             """.trimIndent()
        )!!

        assertThat(o).isEqualTo(new("TestRecord", JsonValue.undefined<Any>()))
    }

    @Test
    @Throws(IOException::class)
    fun jsonReaderUserNullableIsNullable() {
        compile(
            """
            @JsonReader
            data class TestRecord(@field:JsonField("test_field") val testField: JsonNullable<java.sql.Timestamp>)
            """.trimIndent()
        )

        val timestampReader = JsonReader { parser -> Timestamp.from(Instant.ofEpochMilli(parser.longValue)) }

        val o = reader("TestRecord", timestampReader).read(
            """
             {"test_field":null}
             """.trimIndent()
        )!!

        assertThat(o).isEqualTo(new("TestRecord", JsonNullable.nullValue<Any>()))
    }

    @Test
    @Throws(IOException::class)
    fun jsonReaderUserNullableIsPresent() {
        compile(
            """
            @JsonReader
            data class TestRecord(@field:JsonField("test_field") val testField: JsonNullable<java.sql.Timestamp>)
            """.trimIndent()
        )

        val timestampReader = JsonReader { parser -> Timestamp.from(Instant.ofEpochMilli(parser.longValue)) }

        val o = reader("TestRecord", timestampReader).read(
            """
            {"test_field":1}
            """.trimIndent()
        )

        assertThat(o).isEqualTo(new("TestRecord", JsonNullable.of(Timestamp.from(Instant.ofEpochMilli(1)))))
    }

    @Test
    @Throws(IOException::class)
    fun jsonReaderUnknownFieldsAndNativeNullableIsUndefined() {
        compile(
            """
            @JsonReader
            data class TestRecord(@field:JsonField("test_field") val testField: JsonNullable<String>)
            """.trimIndent()
        )

        val o = reader("TestRecord").read(
            """
             {"f1":"1", "f2":"2"}
             """.trimIndent()
        )!!

        assertThat(o).isEqualTo(new("TestRecord", JsonValue.undefined<Any>()))
    }

    @Test
    @Throws(IOException::class)
    fun jsonReaderUnknownFieldsAndNativeNullableIsNullable() {
        compile(
            """
            @JsonReader
            data class TestRecord(@field:JsonField("test_field") val testField: JsonNullable<String>)
            
            """.trimIndent()
        )

        val o = reader("TestRecord").read(
            """
             {"f1":"1", "test_field":null, "f2":"2"}
             """.trimIndent()
        )!!

        assertThat(o).isEqualTo(new("TestRecord", JsonNullable.nullValue<Any>()))
    }

    @Test
    @Throws(IOException::class)
    fun jsonReaderUnknownFieldsAndNativeNullableIsPresent() {
        compile(
            """
            @JsonReader
            data class TestRecord(@field:JsonField("test_field") val testField: JsonNullable<String>)
            
            """.trimIndent()
        )

        val o = reader("TestRecord").read(
            """
             {"f1":"1", "test_field":"test", "f2":"2"}
             """.trimIndent()
        )!!

        assertThat(o).isEqualTo(new("TestRecord", JsonNullable.of("test")))
    }

    @Test
    @Throws(IOException::class)
    fun jsonReaderUnknownFieldsAndUserNullableIsUndefined() {
        compile(
            """
            @JsonReader
            data class TestRecord(@field:JsonField("test_field") val testField: JsonNullable<java.sql.Timestamp>)
            """.trimIndent()
        )

        val timestampReader = JsonReader { parser -> Timestamp.from(Instant.ofEpochMilli(parser.longValue)) }

        val o = reader("TestRecord", timestampReader).read(
            """
             {"f1":"1", "f2":"2"}
             """.trimIndent()
        )!!

        assertThat(o).isEqualTo(new("TestRecord", JsonValue.undefined<Any>()))
    }

    @Test
    @Throws(IOException::class)
    fun jsonReaderUnknownFieldsAndUserNullableIsNullable() {
        compile(
            """
            @JsonReader
            data class TestRecord(@field:JsonField("test_field") val testField: JsonNullable<java.sql.Timestamp>)
            """.trimIndent()
        )

        val timestampReader = JsonReader { parser -> Timestamp.from(Instant.ofEpochMilli(parser.longValue)) }

        val o = reader("TestRecord", timestampReader).read(
            """
             {"f1":"1", "test_field":null, "f2":"2"}
             """.trimIndent()
        )!!

        assertThat(o).isEqualTo(new("TestRecord", JsonNullable.nullValue<Any>()))
    }

    @Test
    @Throws(IOException::class)
    fun jsonReaderUnknownFieldsAndUserNullableIsPresent() {
        compile(
            """
            @JsonReader
            data class TestRecord(@field:JsonField("test_field") val testField: JsonNullable<java.sql.Timestamp>)
            """.trimIndent()
        )

        val timestampReader = JsonReader { parser -> Timestamp.from(Instant.ofEpochMilli(parser.longValue)) }

        val o = reader("TestRecord", timestampReader).read(
            """
            {"f1":"1", "test_field":1, "f2":"2"}
            """.trimIndent()
        )

        assertThat(o).isEqualTo(new("TestRecord", JsonNullable.of(Timestamp.from(Instant.ofEpochMilli(1)))))
    }
}
