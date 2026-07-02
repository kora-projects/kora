package ru.tinkoff.kora.json.ksp

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import ru.tinkoff.kora.json.common.JsonReader
import ru.tinkoff.kora.json.common.JsonWriter

class EnumTest : AbstractJsonSymbolProcessorTest() {
    private var stringReader = JsonReader<String> { obj: JsonParser -> obj.valueAsString }
    private var stringWriter = JsonWriter<String> { obj: JsonGenerator, text: String? -> obj.writeString(text) }

    @Test
    fun testEnum() {
        compile(
            """
            @Json
            enum class TestEnum {
              VALUE1, VALUE2
            }
            
            """.trimIndent()
        )
        compileResult.assertSuccess()
        val mapper = mapper("TestEnum", listOf(stringReader), listOf(stringWriter))
        mapper.assert(enumConstant("TestEnum", "VALUE1"), "\"VALUE1\"")
        mapper.assert(enumConstant("TestEnum", "VALUE2"), "\"VALUE2\"")
    }

    @Test
    fun testEnumWithCustomJsonValue() {
        compile(
            """
            @Json
            enum class TestEnum {
              VALUE1, VALUE2;
              
              @Json
              fun intValue() = ordinal
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()
        val intReader = JsonReader { obj: JsonParser -> obj.intValue }
        val intWriter = JsonWriter { obj: JsonGenerator, v: Int? -> obj.writeNumber(v!!) }
        val mapper = mapper("TestEnum", listOf(intReader), listOf(intWriter))
        mapper.assert(enumConstant("TestEnum", "VALUE1"), "0")
        mapper.assert(enumConstant("TestEnum", "VALUE2"), "1")
    }


    @Test
    fun testReaderFromExtension() {
        compile(
            """
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
            
            """.trimIndent()
        )
        compileResult.assertSuccess()
        Assertions.assertThat(reader("TestApp_TestEnum", stringReader)).isNotNull()
    }

    @Test
    fun testWriterFromExtension() {
        compile(
            """
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
            
            """.trimIndent()
        )
        compileResult.assertSuccess()
        Assertions.assertThat(writer("TestApp_TestEnum", stringWriter)).isNotNull()
    }

    @Test
    fun testAnnotationProcessedReaderFromExtension() {
        compile(
            """
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
            
            """.trimIndent()
        )
        compileResult.assertSuccess()
        Assertions.assertThat(reader("TestApp_TestEnum", stringReader)).isNotNull()
    }

    @Test
    fun testAnnotationProcessedWriterFromExtension() {
        compile(
            """
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
            
            """.trimIndent()
        )
        compileResult.assertSuccess()
        Assertions.assertThat(writer("TestApp_TestEnum", stringWriter)).isNotNull()
    }

    @Test
    fun testEnumReaderFromFactoryMethod() {
        compile(
            """
            @Json
            enum class TestEnum(val value: String) {
              SHARE("share"), BOND("bond"), OTHER("other");
              companion object {
                private val byValue = entries.associateBy { it.value }
                @JsonReader
                fun fromValue(value: String): TestEnum = byValue[value] ?: OTHER
              }
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()

        val r = reader("TestEnum", stringReader)
        r.assertRead("\"share\"", enumConstant("TestEnum", "SHARE"))
        r.assertRead("\"bond\"", enumConstant("TestEnum", "BOND"))
        r.assertRead("\"nonsense\"", enumConstant("TestEnum", "OTHER"))
        r.assertRead("null", null)
    }

    @Test
    fun testEnumReaderFactoryIntValue() {
        compile(
            """
            @Json
            enum class TestEnum(val code: Int) {
              A(1), B(2), OTHER(-1);
              companion object {
                @JsonReader
                fun fromCode(code: Int): TestEnum = entries.firstOrNull { it.code == code } ?: OTHER
              }
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()

        val intReader = JsonReader { p: JsonParser -> p.intValue }
        val r = reader("TestEnum", intReader)
        r.assertRead("1", enumConstant("TestEnum", "A"))
        r.assertRead("2", enumConstant("TestEnum", "B"))
        r.assertRead("99", enumConstant("TestEnum", "OTHER"))
    }

    @Test
    fun testEnumFactoryMultipleReadersFails() {
        compile0(
            listOf(JsonSymbolProcessorProvider()), """
            @Json
            enum class TestEnum(val value: String) {
              A("a"), B("b");
              companion object {
                @JsonReader fun fromValue(value: String): TestEnum = A
                @JsonReader fun fromOther(value: String): TestEnum = B
              }
            }
            """.trimIndent()
        )
        Assertions.assertThat(compileResult.isFailed()).isTrue()
        Assertions.assertThat(compileResult.messages)
            .anyMatch { it.contains("multiple @JsonReader factory") }
    }

    @Test
    fun testEnumFactoryWrongParameterCountFails() {
        compile0(
            listOf(JsonSymbolProcessorProvider()), """
            @Json
            enum class TestEnum(val value: String) {
              A("a"), B("b");
              companion object {
                @JsonReader fun fromValue(value: String, extra: Int): TestEnum = A
              }
            }
            """.trimIndent()
        )
        Assertions.assertThat(compileResult.isFailed()).isTrue()
        Assertions.assertThat(compileResult.messages)
            .anyMatch { it.contains("exactly one parameter") }
    }

    @Test
    fun testEnumFactoryWrongReturnTypeFails() {
        compile0(
            listOf(JsonSymbolProcessorProvider()), """
            @Json
            enum class TestEnum(val value: String) {
              A("a"), B("b");
              companion object {
                @JsonReader fun fromValue(value: String): String = value
              }
            }
            """.trimIndent()
        )
        Assertions.assertThat(compileResult.isFailed()).isTrue()
        Assertions.assertThat(compileResult.messages).anyMatch { it.contains("must return") }
    }

    @Test
    fun testEnumFactoryNonPublicFails() {
        compile0(listOf(JsonSymbolProcessorProvider()), """
            @Json
            enum class TestEnum(val value: String) {
              A("a"), B("b");
              companion object {
                @JsonReader private fun fromValue(value: String): TestEnum = A
              }
            }
            """.trimIndent())
        Assertions.assertThat(compileResult.isFailed()).isTrue()
        Assertions.assertThat(compileResult.messages).anyMatch { it.contains("must be public") }
    }

    @Test
    fun testEnumFactoryNotInCompanionFails() {
        compile0(listOf(JsonSymbolProcessorProvider()), """
            @Json
            enum class TestEnum(val value: String) {
              A("a"), B("b");
              @JsonReader fun fromValue(value: String): TestEnum = A
            }
            """.trimIndent())
        Assertions.assertThat(compileResult.isFailed()).isTrue()
        Assertions.assertThat(compileResult.messages).anyMatch { it.contains("companion") }
    }

    @Test
    fun testEnumReaderFactoryTriggersWithoutJsonAnnotation() {
        compile(
            """
            enum class TestEnum(val value: String) {
              SHARE("share"), OTHER("other");
              companion object {
                @JsonReader
                fun fromValue(value: String): TestEnum = if (value == "share") SHARE else OTHER
              }
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()

        val r = reader("TestEnum", stringReader)
        r.assertRead("\"share\"", enumConstant("TestEnum", "SHARE"))
        r.assertRead("\"x\"", enumConstant("TestEnum", "OTHER"))
    }

    @Test
    fun testJsonReaderFactoryOnNonEnumFails() {
        compile0(
            listOf(JsonSymbolProcessorProvider()), """
            class NotAnEnum(val value: String) {
              companion object {
                @JsonReader
                fun fromValue(value: String): NotAnEnum = NotAnEnum(value)
              }
            }
            """.trimIndent()
        )
        Assertions.assertThat(compileResult.isFailed()).isTrue()
        Assertions.assertThat(compileResult.messages)
            .anyMatch { it.contains("supported only for an enum") }
    }

    @Test
    fun testEnumClassAndFactoryAnnotationUsesFactory() {
        compile(
            """
            @JsonReader
            enum class TestEnum(val value: String) {
              SHARE("share"), OTHER("other");
              companion object {
                @JsonReader
                fun fromValue(value: String): TestEnum = if (value == "share") SHARE else OTHER
              }
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()

        val r = reader("TestEnum", stringReader)
        // @JsonReader on BOTH the enum class and the factory method: the factory reader must be
        // generated (unknown -> OTHER), not the default map-based reader (which would throw), and
        // exactly once — the class-branch and method-branch must dedup via processedReaders.
        r.assertRead("\"share\"", enumConstant("TestEnum", "SHARE"))
        r.assertRead("\"unknown\"", enumConstant("TestEnum", "OTHER"))
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

    @Test
    fun testFactoryReaderFromExtension() {
        compile0(
            listOf(ru.tinkoff.kora.kora.app.ksp.KoraAppProcessorProvider(), JsonSymbolProcessorProvider()), """
        enum class TestEnum(val value: String) {
          SHARE("share"), OTHER("other");
          companion object {
            @JsonReader
            fun fromValue(value: String): TestEnum = if (value == "share") SHARE else OTHER
          }
        }
        """.trimIndent(), """
        @ru.tinkoff.kora.common.KoraApp
        interface TestApp {
          fun stringReader(): ru.tinkoff.kora.json.common.JsonReader<String> = ru.tinkoff.kora.json.common.JsonReader<String> { obj -> obj.valueAsString }

          @Root
          fun root(r: ru.tinkoff.kora.json.common.JsonReader<TestEnum>) = ""
        }
        """.trimIndent()
        )
        compileResult.assertSuccess()
        Assertions.assertThat(reader("TestEnum", stringReader)).isNotNull()
    }
}
