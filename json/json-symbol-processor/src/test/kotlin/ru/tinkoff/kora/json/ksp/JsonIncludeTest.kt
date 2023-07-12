package ru.tinkoff.kora.json.ksp

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import org.junit.jupiter.api.Test
import ru.tinkoff.kora.json.common.ListJsonReader
import ru.tinkoff.kora.json.common.ListJsonWriter

class JsonIncludeTest : AbstractJsonSymbolProcessorTest() {
    @Test
    fun testIncludeAlways() {
        compile("""
            import ru.tinkoff.kora.json.common.annotation.JsonInclude
                            
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
            import ru.tinkoff.kora.json.common.annotation.JsonInclude;
                            
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
            import ru.tinkoff.kora.json.common.annotation.JsonInclude;
                            
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
            import ru.tinkoff.kora.json.common.annotation.JsonInclude;
                            
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
            import ru.tinkoff.kora.json.common.annotation.JsonInclude;
                            
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
}
