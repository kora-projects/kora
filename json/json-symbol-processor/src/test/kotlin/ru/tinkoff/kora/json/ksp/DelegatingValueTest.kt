package ru.tinkoff.kora.json.ksp

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import ru.tinkoff.kora.json.common.JsonReader
import ru.tinkoff.kora.json.common.JsonWriter

class DelegatingValueTest : AbstractJsonSymbolProcessorTest() {
    private val stringReader = JsonReader<String> { p: JsonParser -> p.valueAsString }
    private val stringWriter = JsonWriter<String> { g: JsonGenerator, v: String? -> g.writeString(v) }
    private val longReader = JsonReader<Long> { p: JsonParser -> p.longValue }
    private val longWriter = JsonWriter<Long> { g: JsonGenerator, v: Long? -> g.writeNumber(v!!) }

    private fun newObject(name: String, argType: Class<*>, arg: Any?): Any =
        compileResult.classLoader.loadClass("${testPackage()}.$name").getDeclaredConstructor(argType).newInstance(arg)

    @Test
    fun testDelegatingInstanceWriterAndFactoryReader() {
        compile(
            """
            class UserId(val id: Long) {
              @JsonWriter fun toJson(): Long = id
              companion object { @JsonReader fun of(v: Long): UserId = UserId(v) }
              override fun equals(other: Any?) = other is UserId && other.id == id
              override fun hashCode() = id.hashCode()
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()
        val mapper = mapper("UserId", listOf(longReader), listOf(longWriter))
        mapper.assert(newObject("UserId", Long::class.javaPrimitiveType!!, 42L), "42")
    }

    @Test
    fun testDelegatingStaticWriter() {
        compile(
            """
            class UserId(val id: Long) {
              companion object {
                @JsonReader fun of(v: Long): UserId = UserId(v)
                @JsonWriter fun toJson(u: UserId): Long = u.id
              }
              override fun equals(other: Any?) = other is UserId && other.id == id
              override fun hashCode() = id.hashCode()
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()
        val mapper = mapper("UserId", listOf(longReader), listOf(longWriter))
        mapper.assert(newObject("UserId", Long::class.javaPrimitiveType!!, 7L), "7")
    }

    @Test
    fun testDelegatingStringValue() {
        compile(
            """
            class Sku(val code: String) {
              @JsonWriter fun toJson(): String = code
              companion object { @JsonReader fun parse(v: String): Sku = Sku(v) }
              override fun equals(other: Any?) = other is Sku && other.code == code
              override fun hashCode() = code.hashCode()
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()
        val mapper = mapper("Sku", listOf(stringReader), listOf(stringWriter))
        mapper.assert(newObject("Sku", String::class.java, "ABC"), "\"ABC\"")
    }

    @Test
    fun testDelegatingWriterWinsOverJsonProperties() {
        compile(
            """
            @Json
            class Money(val amount: Long, val currency: String) {
              @JsonWriter fun toJson(): Long = amount
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()
        val w = writer("Money", longWriter)
        val money = compileResult.classLoader.loadClass("${testPackage()}.Money")
            .getDeclaredConstructor(Long::class.javaPrimitiveType, String::class.java)
            .newInstance(100L, "USD")
        w.assertWrite(money, "100")
    }

    @Test
    fun testDelegatingReaderFromExtension() {
        compile(
            """
            @ru.tinkoff.kora.common.KoraApp
            interface TestApp {
              class UserId(val id: Long) {
                companion object { @JsonReader fun of(v: Long): UserId = UserId(v) }
              }

              fun longReader(): ru.tinkoff.kora.json.common.JsonReader<Long> = ru.tinkoff.kora.json.common.JsonReader<Long> { p -> p.longValue }
              fun longWriter(): ru.tinkoff.kora.json.common.JsonWriter<Long> = ru.tinkoff.kora.json.common.JsonWriter<Long> { g, v -> g.writeNumber(v!!) }

              @Root
              fun root(r: ru.tinkoff.kora.json.common.JsonReader<UserId>) = ""
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()
        Assertions.assertThat(reader("TestApp_UserId", longReader)).isNotNull()
    }

    @Test
    fun testDelegatingWriterFromExtension() {
        compile(
            """
            @ru.tinkoff.kora.common.KoraApp
            interface TestApp {
              class UserId(val id: Long) {
                @JsonWriter fun toJson(): Long = id
              }

              fun longReader(): ru.tinkoff.kora.json.common.JsonReader<Long> = ru.tinkoff.kora.json.common.JsonReader<Long> { p -> p.longValue }
              fun longWriter(): ru.tinkoff.kora.json.common.JsonWriter<Long> = ru.tinkoff.kora.json.common.JsonWriter<Long> { g, v -> g.writeNumber(v!!) }

              @Root
              fun root(r: ru.tinkoff.kora.json.common.JsonWriter<UserId>) = ""
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()
        Assertions.assertThat(writer("TestApp_UserId", longWriter)).isNotNull()
    }

    @Test
    fun testMultipleWriterMethodsFails() {
        val res = compile0(
            """
            class UserId(val id: Long) {
              @JsonWriter fun a(): Long = id
              @JsonWriter fun b(): Long = id
            }
            """.trimIndent()
        )
        Assertions.assertThat(res.isFailed()).isTrue()
        Assertions.assertThat(res.messages).anyMatch { it.contains("multiple @JsonWriter methods") }
    }

    @Test
    fun testWriterInstanceMethodWithParamsFails() {
        val res = compile0(
            """
            class UserId(val id: Long) {
              @JsonWriter fun toJson(x: Int): Long = id
            }
            """.trimIndent()
        )
        Assertions.assertThat(res.isFailed()).isTrue()
        Assertions.assertThat(res.messages).anyMatch { it.contains("must have no parameters") }
    }

    @Test
    fun testReaderFactoryNotStaticFails() {
        val res = compile0(
            """
            class UserId(val id: Long) {
              @JsonReader fun of(v: Long): UserId = UserId(v)
            }
            """.trimIndent()
        )
        Assertions.assertThat(res.isFailed()).isTrue()
        Assertions.assertThat(res.messages).anyMatch { it.contains("must be static") }
    }

    @Test
    fun testReaderFactoryWrongReturnFails() {
        val res = compile0(
            """
            class UserId(val id: Long) {
              companion object { @JsonReader fun of(v: Long): String = v.toString() }
            }
            """.trimIndent()
        )
        Assertions.assertThat(res.isFailed()).isTrue()
        Assertions.assertThat(res.messages).anyMatch { it.contains("must return") }
    }

    @Test
    fun testFactoryAndReaderConstructorConflictFails() {
        val res = compile0(
            """
            class UserId @JsonReader constructor(val id: Long) {
              companion object { @JsonReader fun of(v: Long): UserId = UserId(v) }
            }
            """.trimIndent()
        )
        Assertions.assertThat(res.isFailed()).isTrue()
        Assertions.assertThat(res.messages).anyMatch { it.contains("only one is allowed") }
    }

    @Test
    fun testWriterOnTopLevelFunctionFails() {
        val res = compile0(
            """
            class Dummy {
            }

            @JsonWriter
            fun topLevel(): Long = 1L
            """.trimIndent()
        )
        Assertions.assertThat(res.isFailed()).isTrue()
        Assertions.assertThat(res.messages).anyMatch { it.contains("supported only for a method of a class or enum") }
    }
}
