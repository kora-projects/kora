package ru.tinkoff.kora.json.ksp

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets

class JsonFieldTest : AbstractJsonSymbolProcessorTest() {
    @Test
    fun testReaderWithNoSpecifiedAnnotation() {
        compile("""
            @Json
            data class TestClass(@JsonField("test_field") val testField: String)
        """.trimIndent())

        val o = reader("TestClass").read("""{"test_field":"test"}""")
        Assertions.assertThat(o).isEqualTo(new("TestClass", "test"))
    }

    @Test
    fun testReaderWithFieldAnnotation() {
        compile("""
            @Json
            data class TestClass(@field:JsonField("test_field") val testField: String)
        """.trimIndent())

        val o = reader("TestClass").read("""{"test_field":"test"}""")
        Assertions.assertThat(o).isEqualTo(new("TestClass", "test"))
    }

    @Disabled("there's no way to mark java annotation as applicable to property right now")
    @Test
    fun testReaderWithPropertyAnnotation() {
        compile("""
            @Json
            data class TestClass(@property:JsonField("test_field") val testField: String)
        """.trimIndent())

        val o = reader("TestClass").read("""{"test_field":"test"}""")
        Assertions.assertThat(o).isEqualTo(new("TestClass", "test"))
    }

    @Test
    fun testReaderWithParameterAnnotation() {
        compile("""
            @Json
            data class TestClass(@param:JsonField("test_field") val testField: String)
        """.trimIndent())

        val o = reader("TestClass").read("""{"test_field":"test"}""")
        Assertions.assertThat(o).isEqualTo(new("TestClass", "test"))
    }

    @Test
    fun testWriterWithNoSpecifiedAnnotation() {
        compile("""
            @Json
            data class TestClass(@JsonField("test_field") val testField: String)
        """.trimIndent())

        val o = writer("TestClass").toByteArray(new("TestClass", "test"))
        Assertions.assertThat(o).asString(StandardCharsets.UTF_8).isEqualTo("""{"test_field":"test"}""")
    }

    @Test
    fun testWriterWithFieldAnnotation() {
        compile("""
            @Json
            data class TestClass(@field:JsonField("test_field") val testField: String)
        """.trimIndent())

        val o = writer("TestClass").toByteArray(new("TestClass", "test"))
        Assertions.assertThat(o).asString(StandardCharsets.UTF_8).isEqualTo("""{"test_field":"test"}""")
    }

    @Disabled("there's no way to mark java annotation as applicable to property right now")
    @Test
    fun testWriterWithPropertyAnnotation() {
        compile("""
            @Json
            data class TestClass(@property:JsonField("test_field") val testField: String)
        """.trimIndent())

        val o = writer("TestClass").toByteArray(new("TestClass", "test"))
        Assertions.assertThat(o).asString(StandardCharsets.UTF_8).isEqualTo("""{"test_field":"test"}""")
    }
}
