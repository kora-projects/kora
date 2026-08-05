package io.koraframework.json.ksp

import io.koraframework.json.common.JsonReader
import io.koraframework.json.common.JsonWriter
import org.junit.jupiter.api.Test
import tools.jackson.core.JsonGenerator
import tools.jackson.core.JsonParser

class DelegatingValueTest : AbstractJsonSymbolProcessorTest() {
    private val stringReader = JsonReader<String> { p: JsonParser -> p.valueAsString }
    private val stringWriter = JsonWriter<String> { g: JsonGenerator, v: String? -> g.writeString(v) }
    private val longReader = JsonReader<Long> { p: JsonParser -> p.longValue }
    private val longWriter = JsonWriter<Long> { g: JsonGenerator, v: Long? -> g.writeNumber(v!!) }

    private fun newObject(name: String, argType: Class<*>, arg: Any?): Any =
        compileResult.assertSuccess().classLoader.loadClass("${testPackage()}.$name").getDeclaredConstructor(argType).newInstance(arg)

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
}
