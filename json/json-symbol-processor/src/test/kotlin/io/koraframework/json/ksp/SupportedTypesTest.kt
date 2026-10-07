package io.koraframework.json.ksp

import io.koraframework.json.common.JsonReader
import io.koraframework.json.common.reader.ListJsonReader
import io.koraframework.json.common.reader.MapJsonReader
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import tools.jackson.core.JsonToken
import tools.jackson.core.exc.StreamReadException
import java.math.BigInteger
import java.util.*

class SupportedTypesTest : AbstractJsonSymbolProcessorTest() {

    @Test
    fun testInt() {
        compile("""
            @Json
            data class TestRecord(val value: Int)
            """.trimIndent())
        compileResult.assertSuccess()
        val mapper = mapper("TestRecord")
        mapper.assert(new("TestRecord", 42), "{\"value\":42}")
        Assertions.assertThatThrownBy { mapper.read("{\"value\":null}") }.isInstanceOf(StreamReadException::class.java)
    }

    @Test
    fun testNullableInteger() {
        compile("""
            @Json
            data class TestRecord(val value: Int?)
            """.trimIndent())
        compileResult.assertSuccess()
        val mapper = mapper("TestRecord")
        mapper.assert(new("TestRecord", 42), "{\"value\":42}")
        mapper.assert(new("TestRecord", null), "{}")
        mapper.assertRead("{\"value\":null}", new("TestRecord", null))
    }

    @Test
    fun testLong() {
        compile("""
            @Json
            data class TestRecord(val value: Long)
            """.trimIndent())
        compileResult.assertSuccess()
        val mapper = mapper("TestRecord")
        mapper.assert(new("TestRecord", 42L), "{\"value\":42}")
        Assertions.assertThatThrownBy { mapper.read("{\"value\":null}") }.isInstanceOf(StreamReadException::class.java)
    }

    @Test
    fun testNullableLong() {
        compile("""
            @Json
            data class TestRecord(val value: Long?)
            """.trimIndent())
        compileResult.assertSuccess()
        val mapper = mapper("TestRecord")
        mapper.assert(new("TestRecord", 42L), "{\"value\":42}")
        mapper.assert(new("TestRecord", null), "{}")
        mapper.assertRead("{\"value\":null}", new("TestRecord", null))
    }

    @Test
    fun testShort() {
        compile("""
            @Json
            data class TestRecord(val value: Short)
            """.trimIndent())
        compileResult.assertSuccess()
        val mapper = mapper("TestRecord")
        mapper.assert(new("TestRecord", 42.toShort()), "{\"value\":42}")
        Assertions.assertThatThrownBy { mapper.read("{\"value\":null}") }.isInstanceOf(StreamReadException::class.java)
    }

    @Test
    fun testNullableShort() {
        compile("""
            @Json
            data class TestRecord(val value: Short?)
            """.trimIndent())
        compileResult.assertSuccess()
        val mapper = mapper("TestRecord")
        mapper.assert(new("TestRecord", 42.toShort()), "{\"value\":42}")
        mapper.assert(new("TestRecord", null), "{}")
        mapper.assertRead("{\"value\":null}", new("TestRecord", null))
    }

    @Test
    fun testFloat() {
        compile("""
            @Json
            data class TestRecord(val value: Float)
            """.trimIndent())
        compileResult.assertSuccess()
        val mapper = mapper("TestRecord")
        mapper.assert(new("TestRecord", 42.1f), "{\"value\":42.1}")
        mapper.assertRead("{\"value\":42}", new("TestRecord", 42f))
        Assertions.assertThatThrownBy { mapper.read("{\"value\":null}") }.isInstanceOf(StreamReadException::class.java)
    }

    @Test
    fun testNullableFloat() {
        compile("""
            @Json
            data class TestRecord(val value: Float?)
            """.trimIndent())
        compileResult.assertSuccess()
        val mapper = mapper("TestRecord")
        mapper.assert(new("TestRecord", 42.1f), "{\"value\":42.1}")
        mapper.assertRead("{\"value\":42}", new("TestRecord", 42f))
        mapper.assert(new("TestRecord", null), "{}")
        mapper.assertRead("{\"value\":null}", new("TestRecord", null))
    }

    @Test
    fun testDouble() {
        compile("""
            @Json
            data class TestRecord(val value: Double)
            """.trimIndent())
        compileResult.assertSuccess()
        val mapper = mapper("TestRecord")
        mapper.assert(new("TestRecord", 42.1), "{\"value\":42.1}")
        mapper.assertRead("{\"value\":42}", new("TestRecord", 42.0))
        Assertions.assertThatThrownBy { mapper.read("{\"value\":null}") }.isInstanceOf(StreamReadException::class.java)
    }

    @Test
    fun testNullableDouble() {
        compile("""
            @Json
            data class TestRecord(val value: Double?)
            """.trimIndent())
        compileResult.assertSuccess()
        val mapper = mapper("TestRecord")
        mapper.assert(new("TestRecord", 42.1), "{\"value\":42.1}")
        mapper.assertRead("{\"value\":42}", new("TestRecord", 42.0))
        mapper.assert(new("TestRecord", null), "{}")
        mapper.assertRead("{\"value\":null}", new("TestRecord", null))
    }

    @Test
    fun testBoolean() {
        compile("""
            @Json
            data class TestRecord(val value: Boolean)
            """.trimIndent())
        compileResult.assertSuccess()
        val mapper = mapper("TestRecord")
        mapper.assert(new("TestRecord", true), "{\"value\":true}")
        mapper.assert(new("TestRecord", false), "{\"value\":false}")
        Assertions.assertThatThrownBy { mapper.read("{\"value\":null}") }.isInstanceOf(StreamReadException::class.java)
    }

    @Test
    fun testNullableBoolean() {
        compile("""
            @Json
            data class TestRecord(val value: Boolean?)
            """.trimIndent())
        compileResult.assertSuccess()
        val mapper = mapper("TestRecord")
        mapper.assert(new("TestRecord", true), "{\"value\":true}")
        mapper.assert(new("TestRecord", false), "{\"value\":false}")
        mapper.assert(new("TestRecord", null), "{}")
        mapper.assertRead("{\"value\":null}", new("TestRecord", null))
    }

    @Test
    fun testString() {
        compile("""
            @Json
            data class TestRecord(val value: String)
            """.trimIndent())
        compileResult.assertSuccess()
        val mapper = mapper("TestRecord")
        mapper.assert(new("TestRecord", "test"), "{\"value\":\"test\"}")
        Assertions.assertThatThrownBy { mapper.read("{\"value\":null}") }.isInstanceOf(StreamReadException::class.java)
    }

    @Test
    fun testNullableString() {
        compile("""
            @Json
            data class TestRecord(val value: String?)
            """.trimIndent())
        compileResult.assertSuccess()
        val mapper = mapper("TestRecord")
        mapper.assert(new("TestRecord", "test"), "{\"value\":\"test\"}")
        mapper.assert(new("TestRecord", null), "{}")
        mapper.assertRead("{\"value\":null}", new("TestRecord", null))
    }

    @Test
    fun testUuid() {
        compile("""
            @Json
            data class TestRecord(val value: java.util.UUID)
            """.trimIndent())
        compileResult.assertSuccess()
        val uuid = UUID.randomUUID()
        val mapper = mapper("TestRecord")
        mapper.assert(new("TestRecord", uuid), "{\"value\":\"$uuid\"}")
        Assertions.assertThatThrownBy { mapper.read("{\"value\":null}") }.isInstanceOf(StreamReadException::class.java)
    }

    @Test
    fun testNullableUuid() {
        compile("""
            @Json
            data class TestRecord(val value: java.util.UUID?)
            """.trimIndent())
        compileResult.assertSuccess()
        val uuid = UUID.randomUUID()
        val mapper = mapper("TestRecord")
        mapper.assert(new("TestRecord", uuid), "{\"value\":\"$uuid\"}")
        mapper.assert(new("TestRecord", null), "{}")
        mapper.assertRead("{\"value\":null}", new("TestRecord", null))
    }

    @Test
    fun testBigInteger() {
        compile("""
            @Json
            data class TestRecord(val value: java.math.BigInteger)
            """.trimIndent())
        compileResult.assertSuccess()
        val mapper = mapper("TestRecord")
        mapper.assert(new("TestRecord", BigInteger("42")), "{\"value\":42}")
        Assertions.assertThatThrownBy { mapper.read("{\"value\":null}") }.isInstanceOf(StreamReadException::class.java)
    }

    @Test
    fun testNullableBigInteger() {
        compile("""
            @Json
            data class TestRecord(val value: java.math.BigInteger?)
            """.trimIndent())
        compileResult.assertSuccess()
        val mapper = mapper("TestRecord")
        mapper.assert(new("TestRecord", BigInteger("42")), "{\"value\":42}")
        mapper.assert(new("TestRecord", null), "{}")
        mapper.assertRead("{\"value\":null}", new("TestRecord", null))
    }

    @Test
    fun testBinary() {
        compile("""
            @Json
            data class TestRecord(val value: ByteArray) {
                override fun equals(that: Any?) = that is TestRecord && java.util.Arrays.equals(this.value, that.value)
            }
            
            """.trimIndent())
        compileResult.assertSuccess()
        val b = byteArrayOf(1, 2, 3, 4)
        val mapper = mapper("TestRecord")
        mapper.assert(new("TestRecord", *arrayOf<Any>(b)), "{\"value\":\"AQIDBA==\"}")
        Assertions.assertThatThrownBy { mapper.read("{\"value\":null}") }.isInstanceOf(StreamReadException::class.java)
    }

    @Test
    fun testNullableBinary() {
        compile("""
            @Json
            data class TestRecord(val value: ByteArray?) {
                override fun equals(that: Any?) = that is TestRecord && java.util.Arrays.equals(this.value, that.value)
            }
            
            """.trimIndent())
        compileResult.assertSuccess()
        val b = byteArrayOf(1, 2, 3, 4)
        val mapper = mapper("TestRecord")
        mapper.assert(new("TestRecord", *arrayOf<Any>(b)), "{\"value\":\"AQIDBA==\"}")
        mapper.assert(new("TestRecord", null), "{}")
        mapper.assertRead("{\"value\":null}", new("TestRecord", null))
    }

    @Test
    fun testTypeAliasOfNullableType() {
        compile("""
            typealias MaybeName = String?
            @Json
            data class TestRecord(val a: String, val n: MaybeName)
            """.trimIndent())
        val mapper = mapper("TestRecord")
        mapper.assert(new("TestRecord", "x", "y"), "{\"a\":\"x\",\"n\":\"y\"}")
        mapper.assert(new("TestRecord", "x", null), "{\"a\":\"x\"}")
        mapper.assertRead("{\"a\":\"x\",\"n\":null}", new("TestRecord", "x", null))
    }

    @Test
    fun testNullableTypeAlias() {
        compile("""
            typealias Name = String
            @Json
            data class TestRecord(val a: String, val n: Name?)
            """.trimIndent())
        val mapper = mapper("TestRecord")
        mapper.assert(new("TestRecord", "x", "y"), "{\"a\":\"x\",\"n\":\"y\"}")
        mapper.assert(new("TestRecord", "x", null), "{\"a\":\"x\"}")
        mapper.assertRead("{\"a\":\"x\",\"n\":null}", new("TestRecord", "x", null))
    }

    @Test
    fun testNullElementInNonNullList() {
        compile("""
            @Json
            data class TestRecord(val value: List<String>)
            """.trimIndent())
        val reader = reader("TestRecord", ListJsonReader(stringReader))
        reader.assertRead("{\"value\":[\"x\"]}", new("TestRecord", listOf("x")))
        Assertions.assertThatThrownBy { reader.read("{\"value\":[\"x\",null]}") }
            .isInstanceOf(StreamReadException::class.java)
            .hasMessageContaining("TestRecord.value")
    }

    @Test
    fun testNullElementInNullableElementList() {
        compile("""
            @Json
            data class TestRecord(val value: List<String?>)
            """.trimIndent())
        val reader = reader("TestRecord", ListJsonReader(stringReader))
        reader.assertRead("{\"value\":[\"x\",null]}", new("TestRecord", listOf("x", null)))
    }

    @Test
    fun testNullValueInNonNullMap() {
        compile("""
            @Json
            data class TestRecord(val value: Map<String, Int>?)
            """.trimIndent())
        val reader = reader("TestRecord", MapJsonReader(intReader))
        reader.assertRead("{\"value\":{\"a\":1}}", new("TestRecord", mapOf("a" to 1)))
        reader.assertRead("{\"value\":null}", new("TestRecord", null))
        Assertions.assertThatThrownBy { reader.read("{\"value\":{\"a\":null}}") }
            .isInstanceOf(StreamReadException::class.java)
            .hasMessageContaining("TestRecord.value")
    }

    @Test
    fun testNullValueInNullableValueMap() {
        compile("""
            @Json
            data class TestRecord(val value: Map<String, Int?>)
            """.trimIndent())
        val reader = reader("TestRecord", MapJsonReader(intReader))
        reader.assertRead("{\"value\":{\"a\":null}}", new("TestRecord", mapOf("a" to null)))
    }

    @Test
    fun testInternalClass() {
        compile("""
            @Json
            internal data class TestRecord(val value: Int)
            """.trimIndent())
        mapper("TestRecord").assert(new("TestRecord", 42), "{\"value\":42}")
    }

    @Test
    fun testInternalEnum() {
        compile("""
            @Json
            internal enum class TestEnum { A, B }
            """.trimIndent())
        compileResult.assertSuccess()
    }

    @Test
    fun testInternalSealedInterface() {
        compile("""
            @Json
            @JsonDiscriminatorField("@type")
            internal sealed interface TestSealed {
                @Json
                data class A(val value: Int) : TestSealed
            }
            """.trimIndent())
        compileResult.assertSuccess()
    }

    private val stringReader = JsonReader<String?> { p -> if (p.currentToken() == JsonToken.VALUE_NULL) null else p.string }
    private val intReader = JsonReader<Int?> { p -> if (p.currentToken() == JsonToken.VALUE_NULL) null else p.intValue }
}
