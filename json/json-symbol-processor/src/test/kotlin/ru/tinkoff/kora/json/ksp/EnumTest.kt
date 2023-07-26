package ru.tinkoff.kora.json.ksp

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import ru.tinkoff.kora.json.common.JsonReader
import ru.tinkoff.kora.json.common.JsonWriter
import ru.tinkoff.kora.kora.app.ksp.KoraAppProcessorProvider

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
    fun testReaderFromExtension() {
        compile(listOf(KoraAppProcessorProvider()), """
            @ru.tinkoff.kora.common.KoraApp
            interface TestApp {
              enum class TestEnum {
                VALUE1, VALUE2
              }
              
              fun stringReader(): ru.tinkoff.kora.json.common.JsonReader<String> = ru.tinkoff.kora.json.common.JsonReader<String> { obj -> obj.valueAsString }
              fun stringWriter(): ru.tinkoff.kora.json.common.JsonWriter<String> = ru.tinkoff.kora.json.common.JsonWriter<String> { obj, text -> obj.writeString(text) }

              @Root
              fun root(r: ru.tinkoff.kora.json.common.JsonReader<TestEnum>) = ""
            }
            
            """.trimIndent())
        compileResult.assertSuccess()
        Assertions.assertThat(reader("TestApp_TestEnum", stringReader)).isNotNull()
    }

    @Test
    fun testWriterFromExtension() {
        compile(listOf(KoraAppProcessorProvider()), """
            @ru.tinkoff.kora.common.KoraApp
            interface TestApp {
              enum class TestEnum {
                VALUE1, VALUE2
              }

              fun stringReader(): ru.tinkoff.kora.json.common.JsonReader<String> = ru.tinkoff.kora.json.common.JsonReader<String> { obj -> obj.valueAsString }
              fun stringWriter(): ru.tinkoff.kora.json.common.JsonWriter<String> = ru.tinkoff.kora.json.common.JsonWriter<String> { obj, text -> obj.writeString(text) }

              @Root
              fun root(r: ru.tinkoff.kora.json.common.JsonWriter<TestEnum>) = ""
            }
            
            """.trimIndent())
        compileResult.assertSuccess()
        Assertions.assertThat(writer("TestApp_TestEnum", stringWriter)).isNotNull()
    }

    @Test
    fun testAnnotationProcessedReaderFromExtension() {
        compile(listOf(KoraAppProcessorProvider(), JsonSymbolProcessorProvider()), """
            @ru.tinkoff.kora.common.KoraApp
            public interface TestApp {
              @Json
              enum class TestEnum {
                VALUE1, VALUE2
              }

              fun stringReader(): ru.tinkoff.kora.json.common.JsonReader<String> = ru.tinkoff.kora.json.common.JsonReader<String> { obj -> obj.valueAsString }
              fun stringWriter(): ru.tinkoff.kora.json.common.JsonWriter<String> = ru.tinkoff.kora.json.common.JsonWriter<String> { obj, text -> obj.writeString(text) }

              @Root
              fun root(r: ru.tinkoff.kora.json.common.JsonReader<TestEnum>) = ""
            }
            
            """.trimIndent())
        compileResult.assertSuccess()
        Assertions.assertThat(reader("TestApp_TestEnum", stringReader)).isNotNull()
    }

    @Test
    fun testAnnotationProcessedWriterFromExtension() {
        compile(listOf(KoraAppProcessorProvider(), JsonSymbolProcessorProvider()), """
            @ru.tinkoff.kora.common.KoraApp
            interface TestApp {
              @Json
              enum class TestEnum {
                VALUE1, VALUE2
              }

              fun stringReader(): ru.tinkoff.kora.json.common.JsonReader<String> = ru.tinkoff.kora.json.common.JsonReader<String> { obj -> obj.valueAsString }
              fun stringWriter(): ru.tinkoff.kora.json.common.JsonWriter<String> = ru.tinkoff.kora.json.common.JsonWriter<String> { obj, text -> obj.writeString(text) }

              @Root
              fun root(r: ru.tinkoff.kora.json.common.JsonWriter<TestEnum>) = ""
            }
            
            """.trimIndent())
        compileResult.assertSuccess()
        Assertions.assertThat(writer("TestApp_TestEnum", stringWriter)).isNotNull()
    }

    private fun enumConstant(className: String, name: String): Any {
        val clazz = this.compileResult.loadClass(className);
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
