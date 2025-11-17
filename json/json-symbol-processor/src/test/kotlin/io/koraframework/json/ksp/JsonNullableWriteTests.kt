package ru.tinkoff.kora.json.ksp

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.tinkoff.kora.json.common.JsonNullable
import ru.tinkoff.kora.json.common.JsonValue
import ru.tinkoff.kora.json.common.JsonWriter
import ru.tinkoff.kora.json.common.ListJsonWriter
import tools.jackson.core.JsonGenerator
import java.sql.Timestamp
import java.time.Instant

class JsonNullableWriteTests : AbstractJsonSymbolProcessorTest() {

    @Test
    fun jsonWriterNativeNullableIsUndefined() {
        compile(
            """
            @JsonWriter
            data class TestRecord(@field:JsonField("test_field") val testField: JsonNullable<String>)
            """.trimIndent()
        )

        val o = writer("TestRecord").toString(new("TestRecord", JsonValue.undefined<Any>()))

        assertThat(o).isEqualTo(
            """
            {}
            """.trimIndent()
        )
    }

    @Test
    fun jsonWriterNativeNullableIsNullable() {
        compile(
            """
            @JsonWriter
            data class TestRecord(@field:JsonField("test_field") val testField: JsonNullable<String>)
            """.trimIndent()
        )

        val o = writer("TestRecord").toString(new("TestRecord", JsonNullable.nullValue<Any>()))

        assertThat(o).isEqualTo(
            """
            {"test_field":null}
            """.trimIndent()
        )
    }

    @Test
    fun jsonWriterNativeNullableIsPresent() {
        compile(
            """
            @JsonWriter
            data class TestRecord(@field:JsonField("test_field") val testField: JsonNullable<String>)
            """.trimIndent()
        )

        val o = writer("TestRecord").toString(new("TestRecord", JsonNullable.of("test")))

        assertThat(o).isEqualTo(
            """
            {"test_field":"test"}
            """.trimIndent()
        )
    }

    @Test
    fun jsonWriterUserNullableIsUndefined() {
        compile(
            """
            @JsonWriter
            data class TestRecord(@field:JsonField("test_field") val testField: JsonNullable<java.sql.Timestamp>)
            """.trimIndent()
        )

        val timeWriter: JsonWriter<Timestamp> = JsonWriter { generator, `object` ->
            if (`object` != null) {
                generator.writeNumber(`object`.time)
            }
        }

        val o = writer("TestRecord", timeWriter).toString(new("TestRecord", JsonValue.undefined<Any>()))

        assertThat(o).isEqualTo(
            """
            {}
            """.trimIndent()
        )
    }

    @Test
    fun jsonWriterUserNullableIsNullable() {
        compile(
            """
            @JsonWriter
            data class TestRecord(@field:JsonField("test_field") val testField: JsonNullable<java.sql.Timestamp>)
            """.trimIndent()
        )

        val timeWriter: JsonWriter<Timestamp> = JsonWriter { generator, `object` ->
            if (`object` != null) {
                generator.writeNumber(`object`.time)
            }
        }

        val o = writer("TestRecord", timeWriter).toString(new("TestRecord", JsonNullable.nullValue<Any>()))

        assertThat(o).isEqualTo(
            """
            {"test_field":null}
            """.trimIndent()
        )
    }

    @Test
    fun jsonWriterUserNullableIsPresent() {
        compile(
            """
            @JsonWriter
            data class TestRecord(@field:JsonField("test_field") val testField: JsonNullable<java.sql.Timestamp>)
            """.trimIndent()
        )

        val timeWriter: JsonWriter<Timestamp> = JsonWriter { generator, `object` ->
            if (`object` != null) {
                generator.writeNumber(`object`.time)
            }
        }

        val o = writer("TestRecord", timeWriter).toString(new("TestRecord", JsonNullable.of(Timestamp.from(Instant.ofEpochMilli(1)))))

        assertThat(o).isEqualTo(
            """
            {"test_field":1}
            """.trimIndent()
        )
    }

    @Test
    fun jsonWriterUserInnerNullableIsUndefined() {
        compile(
            """
            @JsonWriter
            data class TestRecord(@field:JsonField("test_field") val testField: JsonNullable<java.sql.Timestamp>, val inner: JsonNullable<InnerRecord>)
            """.trimIndent(),
            """
            @JsonWriter
            data class InnerRecord(val simpleField: JsonNullable<java.sql.Timestamp>) 
            """.trimIndent(),
        )

        val timeWriter: JsonWriter<Timestamp> = JsonWriter { generator, `object` ->
            if (`object` != null) {
                generator.writeNumber(`object`.time)
            }
        }

        val o = writer("TestRecord", timeWriter, writer("InnerRecord", timeWriter)).toString(
            new("TestRecord", JsonValue.undefined<Any>(), JsonValue.undefined<Any>()))

        assertThat(o).isEqualTo(
            """
            {}
            """.trimIndent()
        )
    }

    @Test
    fun jsonWriterUserInnerNullableIsNullable() {
        compile(
            """
            @JsonWriter
            data class TestRecord(@field:JsonField("test_field") val testField: JsonNullable<java.sql.Timestamp>, val inner: JsonNullable<InnerRecord>)
            """.trimIndent(),
            """
            @JsonWriter
            data class InnerRecord(val simpleField: JsonNullable<java.sql.Timestamp>) 
            """.trimIndent(),
        )

        val timeWriter: JsonWriter<Timestamp> = JsonWriter { generator, `object` ->
            if (`object` != null) {
                generator.writeNumber(`object`.time)
            }
        }

        val o = writer("TestRecord", timeWriter, writer("InnerRecord", timeWriter)).toString(
            new("TestRecord", JsonNullable.nullValue<Any>(), JsonNullable.nullValue<Any>()))

        assertThat(o).isEqualTo(
            """
            {"test_field":null,"inner":null}
            """.trimIndent()
        )
    }

    @Test
    fun jsonWriterUserInnerNullableIsPresent() {
        compile(
            """
            @JsonWriter
            data class TestRecord(@field:JsonField("test_field") val testField: JsonNullable<java.sql.Timestamp>, val inner: JsonNullable<InnerRecord>)
            """.trimIndent(),
            """
            @JsonWriter
            data class InnerRecord(val simpleField: JsonNullable<java.sql.Timestamp>) 
            """.trimIndent(),
            )

        val timeWriter: JsonWriter<Timestamp> = JsonWriter { generator, `object` ->
            if (`object` != null) {
                generator.writeNumber(`object`.time)
            }
        }

        val o = writer("TestRecord", timeWriter, writer("InnerRecord", timeWriter)).toString(
            new("TestRecord", JsonNullable.of(Timestamp.from(Instant.ofEpochMilli(1))),
                JsonNullable.of(new("InnerRecord", JsonNullable.of(Timestamp.from(Instant.ofEpochMilli(1)))))))

        assertThat(o).isEqualTo(
            """
            {"test_field":1,"inner":{"simpleField":1}}
            """.trimIndent()
        )
    }

    @Test
    fun jsonWriterUserNullableNotEmptyIsEmpty() {
        compile(
            """
            @JsonWriter
            data class TestRecord(@field:JsonInclude(IncludeType.NON_EMPTY) @field:JsonField("test_field") val testField: JsonNullable<List<java.sql.Timestamp>>)
            """.trimIndent()
        )

        val timeWriter = ListJsonWriter { generator: JsonGenerator, `object`: Timestamp? ->
            if (`object` != null) {
                generator.writeNumber(`object`.time)
            }
        }

        val o = writer("TestRecord", timeWriter).toString(new("TestRecord", JsonNullable.of<List<Any>>(listOf())))

        assertThat(o).isEqualTo(
            """
            {}
            """.trimIndent()
        )
    }

    @Test
    fun jsonWriterUserNullableNotEmptyIsPresent() {
        compile(
            """
            @JsonWriter
            data class TestRecord(@field:JsonInclude(IncludeType.NON_EMPTY) @field:JsonField("test_field") val testField: JsonNullable<List<java.sql.Timestamp>>)
            """.trimIndent()
        )

        val timeWriter = ListJsonWriter { generator: JsonGenerator, `object`: Timestamp? ->
            if (`object` != null) {
                generator.writeNumber(`object`.time)
            }
        }

        val o = writer("TestRecord", timeWriter).toString(new("TestRecord", JsonNullable.of<List<Timestamp>>(listOf(Timestamp.from(Instant.ofEpochMilli(1))))))

        assertThat(o).isEqualTo(
            """
            {"test_field":[1]}
            """.trimIndent()
        )
    }

    @Test
    fun jsonWriterUserNullableAlwaysIsEmpty() {
        compile(
            """
            @JsonWriter
            data class TestRecord(@field:JsonInclude(IncludeType.ALWAYS) @field:JsonField("test_field") val testField: JsonNullable<List<java.sql.Timestamp>>)
            """.trimIndent()
        )

        val timeWriter = ListJsonWriter { generator: JsonGenerator, `object`: Timestamp? ->
            if (`object` != null) {
                generator.writeNumber(`object`.time)
            }
        }

        val o = writer("TestRecord", timeWriter).toString(new("TestRecord", JsonNullable.of<List<Any>>(listOf())))

        assertThat(o).isEqualTo(
            """
            {"test_field":[]}
            """.trimIndent()
        )
    }

    @Test
    fun jsonWriterUserNullableAlwaysIsPresent() {
        compile(
            """
            @JsonWriter
            data class TestRecord(@field:JsonInclude(IncludeType.ALWAYS) @field:JsonField("test_field") val testField: JsonNullable<List<java.sql.Timestamp>>)
            """.trimIndent()
        )

        val timeWriter = ListJsonWriter { generator: JsonGenerator, `object`: Timestamp? ->
            if (`object` != null) {
                generator.writeNumber(`object`.time)
            }
        }

        val o = writer("TestRecord", timeWriter).toString(new("TestRecord", JsonNullable.of<List<Timestamp>>(listOf(Timestamp.from(Instant.ofEpochMilli(1))))))

        assertThat(o).isEqualTo(
            """
            {"test_field":[1]}
            """.trimIndent()
        )
    }
}
