package io.koraframework.json.ksp

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import io.koraframework.json.common.JsonReader
import io.koraframework.json.common.JsonWriter
import tools.jackson.core.JsonGenerator
import tools.jackson.core.JsonParser

class EnumTest : AbstractJsonSymbolProcessorTest() {
    private var stringReader = JsonReader<String> { obj: JsonParser -> obj.valueAsString }
    private var stringWriter = JsonWriter<String> { obj: JsonGenerator, text: String? -> obj.writeString(text) }

    @Test
    fun testEnum() {
        compile("""
            @Json
            enum class TestEnum {
              VALUE1, VALUE2
            }
            
            """.trimIndent())
        compileResult.assertSuccess()
        val mapper = mapper("TestEnum", listOf(stringReader), listOf(stringWriter))
        mapper.assert(enumConstant("TestEnum", "VALUE1"), "\"VALUE1\"")
        mapper.assert(enumConstant("TestEnum", "VALUE2"), "\"VALUE2\"")
    }

    @Test
    fun testEnumWithCustomJsonValue() {
        compile("""
            @Json
            enum class TestEnum {
              VALUE1, VALUE2;
              
              @Json
              fun intValue() = ordinal
            }
            """.trimIndent())
        compileResult.assertSuccess()
        val intReader = JsonReader { obj: JsonParser -> obj.intValue }
        val intWriter = JsonWriter { obj: JsonGenerator, v: Int? -> obj.writeNumber(v!!) }
        val mapper = mapper("TestEnum", listOf(intReader), listOf(intWriter))
        mapper.assert(enumConstant("TestEnum", "VALUE1"), "0")
        mapper.assert(enumConstant("TestEnum", "VALUE2"), "1")
    }


    @Test
    fun testAnnotationProcessedReaderFromExtension() {
        compile("""
            @io.koraframework.common.KoraApp
            public interface TestApp {
              @Json
              enum class TestEnum {
                VALUE1, VALUE2
              }

              fun stringReader(): io.koraframework.json.common.JsonReader<String> = io.koraframework.json.common.JsonReader<String> { obj -> obj.valueAsString }
              fun stringWriter(): io.koraframework.json.common.JsonWriter<String> = io.koraframework.json.common.JsonWriter<String> { obj, text -> obj.writeString(text) }

              @Root
              fun root(r: io.koraframework.json.common.JsonReader<TestEnum>) = ""
            }
            
            """.trimIndent())
        compileResult.assertSuccess()
        Assertions.assertThat(reader("TestApp_TestEnum", stringReader)).isNotNull()
    }

    @Test
    fun testAnnotationProcessedWriterFromExtension() {
        compile("""
            @io.koraframework.common.KoraApp
            interface TestApp {
              @Json
              enum class TestEnum {
                VALUE1, VALUE2
              }

              fun stringReader(): io.koraframework.json.common.JsonReader<String> = io.koraframework.json.common.JsonReader<String> { obj -> obj.valueAsString }
              fun stringWriter(): io.koraframework.json.common.JsonWriter<String> = io.koraframework.json.common.JsonWriter<String> { obj, text -> obj.writeString(text) }

              @Root
              fun root(r: io.koraframework.json.common.JsonWriter<TestEnum>) = ""
            }
            
            """.trimIndent())
        compileResult.assertSuccess()
        Assertions.assertThat(writer("TestApp_TestEnum", stringWriter)).isNotNull()
    }

    private fun enumConstant(className: String, name: String): Any {
        val clazz = this.loadClass(className);
        require(clazz.isEnum)
        for (enumConstant in clazz.enumConstants) {
            val e = enumConstant as Enum<*>
            if (e.name == name) {
                return e;
            }
        }
        throw RuntimeException("Invalid enum constant: $name");
    }
}
