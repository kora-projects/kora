package io.koraframework.json.ksp

import org.junit.jupiter.api.Test
import io.koraframework.json.common.reader.ListJsonReader
import io.koraframework.json.common.writer.ListJsonWriter
import tools.jackson.core.JsonGenerator
import tools.jackson.core.JsonParser

class JsonIncludeTest : AbstractJsonSymbolProcessorTest() {
    @Test
    fun testIncludeAlways() {
        compile("""
            import io.koraframework.json.common.annotation.JsonInclude
                            
            @JsonInclude(JsonInclude.IncludeType.ALWAYS)
            @Json
            data class TestRecord(val value: Int?)
            """
            .trimIndent()
        )
        compileResult.assertSuccess()
        val mapper = mapper("TestRecord")
        mapper.assertWrite(new("TestRecord", 42), "{\"value\":42}")
        mapper.assertWrite(new("TestRecord", null), "{\"value\":null}")
    }

    @Test
    fun testIncludeNonNull() {
        compile("""
            import io.koraframework.json.common.annotation.JsonInclude;
                            
            @JsonInclude(JsonInclude.IncludeType.NON_NULL)
            @Json
            data class TestRecord(val value: Int?)
            """
            .trimIndent()
        )
        compileResult.assertSuccess()
        val mapper = mapper("TestRecord")
        mapper.assertWrite(new("TestRecord", 42), "{\"value\":42}")
        mapper.assertWrite(new("TestRecord", null), "{}")
    }

    @Test
    fun testIncludeNonEmpty() {
        compile("""
            import io.koraframework.json.common.annotation.JsonInclude;
                            
            @JsonInclude(JsonInclude.IncludeType.NON_EMPTY)
            @Json
            data class TestRecord(val value: List<Int>?)
            
            """
            .trimIndent()
        )
        compileResult.assertSuccess()
        val mapper = mapper("TestRecord", listOf(ListJsonReader<Int> { obj: JsonParser -> obj.intValue }), listOf(ListJsonWriter<Int> { obj: JsonGenerator, v: Int? -> obj.writeNumber(v!!) }))
        mapper.assertWrite(new("TestRecord", listOf(42)), "{\"value\":[42]}")
        mapper.assertWrite(new("TestRecord", listOf<Any>()), "{}")
        mapper.assertWrite(new("TestRecord", null), "{}")
    }

    @Test
    fun testFieldIncludeAlways() {
        compile("""
            import io.koraframework.json.common.annotation.JsonInclude;
                            
            @Json
            data class TestRecord(val name: String?, @JsonInclude(JsonInclude.IncludeType.ALWAYS) val value: Int?)
            """
            .trimIndent()
        )
        compileResult.assertSuccess()
        val mapper = mapper("TestRecord")
        mapper.assertWrite(new("TestRecord", "test", 42), "{\"name\":\"test\",\"value\":42}")
        mapper.assertWrite(new("TestRecord", null, null), "{\"value\":null}")
    }

    @Test
    fun testFieldIncludeNonEmpty() {
        compile("""
            import io.koraframework.json.common.annotation.JsonInclude;
                            
            @Json
            data class TestRecord(@JsonInclude(JsonInclude.IncludeType.NON_EMPTY) val value: List<Int>?)
            """
            .trimIndent()
        )
        compileResult.assertSuccess()
        val mapper = mapper("TestRecord", listOf(ListJsonReader<Int> { obj: JsonParser -> obj.intValue }), listOf(ListJsonWriter<Int> { obj: JsonGenerator, v: Int? -> obj.writeNumber(v!!) }))
        mapper.assertWrite(new("TestRecord", listOf(42)), "{\"value\":[42]}")
        mapper.assertWrite(new("TestRecord", listOf<Any>()), "{}")
        mapper.assertWrite(new("TestRecord", null), "{}")
    }

    @Test
    fun testIncludeAlwaysNonNullFieldsCompileWithoutWarnings() {
        allWarningsAsErrors = true
        compile("""
            import io.koraframework.json.common.annotation.JsonInclude
                            
            @JsonInclude(JsonInclude.IncludeType.ALWAYS)
            @Json
            data class TestRecord(val name: String, val count: Int)
            """
            .trimIndent()
        )
        compileResult.assertSuccess()
        val mapper = mapper("TestRecord")
        mapper.assertWrite(new("TestRecord", "test", 42), "{\"name\":\"test\",\"count\":42}")
    }

    @Test
    fun testFieldIncludeAlwaysNonNullCompilesWithoutWarnings() {
        allWarningsAsErrors = true
        compile("""
            import io.koraframework.json.common.annotation.JsonInclude
                            
            @Json
            data class TestRecord(@JsonInclude(JsonInclude.IncludeType.ALWAYS) val name: String)
            """
            .trimIndent()
        )
        compileResult.assertSuccess()
        val mapper = mapper("TestRecord")
        mapper.assertWrite(new("TestRecord", "test"), "{\"name\":\"test\"}")
    }

    @Test
    fun testIncludeAlwaysNullableTypeAlias() {
        compile("""
            import io.koraframework.json.common.annotation.JsonInclude

            typealias NInt = Int?
            typealias NNInt = NInt
            typealias PlainInt = Int

            @JsonInclude(JsonInclude.IncludeType.ALWAYS)
            @Json
            data class TestRecord(val a: NInt, val b: NNInt, val c: PlainInt)
            """
            .trimIndent()
        )
        compileResult.assertSuccess()
        val mapper = mapper("TestRecord")
        mapper.assertWrite(new("TestRecord", null, null, 1), "{\"a\":null,\"b\":null,\"c\":1}")
        mapper.assertWrite(new("TestRecord", 2, 3, 4), "{\"a\":2,\"b\":3,\"c\":4}")
    }
}
