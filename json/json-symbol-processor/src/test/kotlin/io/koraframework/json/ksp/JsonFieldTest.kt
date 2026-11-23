package io.koraframework.json.ksp

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets

class JsonFieldTest : AbstractJsonSymbolProcessorTest() {
    @Test
    fun testReaderCompilesWithoutWarnings() {
        allWarningsAsErrors = true
        compile("""
            @Json
            data class TestClass(val stringField: String, val intField: Int, val nullableField: String?)
        """.trimIndent())

        val o = reader("TestClass").read("""{"stringField":"test","intField":1,"nullableField":null}""")
        Assertions.assertThat(o).isEqualTo(new("TestClass", "test", 1, null))
    }

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

    @Test
    fun testWriterWithPropertyAnnotation() {
        compile("""
            @Json
            data class TestClass(@property:JsonField("test_field") val testField: String)
        """.trimIndent())

        val o = writer("TestClass").toByteArray(new("TestClass", "test"))
        Assertions.assertThat(o).asString(StandardCharsets.UTF_8).isEqualTo("""{"test_field":"test"}""")
    }

    @Test
    fun testPropertyAndParamAnnotationAsGeneratedByOpenapi() {
        compile("""
            @Json
            data class TestClass(@property:JsonField(value = "test_field") @param:JsonField(value = "test_field") val testField: String)
        """.trimIndent())

        val o = reader("TestClass").read("""{"test_field":"test"}""")
        Assertions.assertThat(o).isEqualTo(new("TestClass", "test"))
        val w = writer("TestClass").toByteArray(new("TestClass", "test"))
        Assertions.assertThat(w).asString(StandardCharsets.UTF_8).isEqualTo("""{"test_field":"test"}""")
    }
}
