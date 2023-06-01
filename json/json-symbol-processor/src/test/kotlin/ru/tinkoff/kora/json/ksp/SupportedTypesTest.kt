package ru.tinkoff.kora.json.ksp

import com.fasterxml.jackson.core.JsonParseException
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.math.BigInteger
import java.util.*

class SupportedTypesTest : AbstractJsonSymbolProcessorTest() {
    @Test
    fun testInt() {
        compile("""
            @Json
            data class TestRecord(val value: Int) {
            }
            
            """.trimIndent())
        compileResult.assertSuccess()
        val mapper = mapper("TestRecord")
        mapper.assert(new("TestRecord", 42), "{\"value\":42}")
        Assertions.assertThatThrownBy { mapper.read("{\"value\":null}") }.isInstanceOf(JsonParseException::class.java)
    }

    @Test
    fun testNullableInteger() {
        compile("""
            @Json
            data class TestRecord(val value: Int?) {
            }
            
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
            data class TestRecord(val value: Long) {
            }
            
            """.trimIndent())
        compileResult.assertSuccess()
        val mapper = mapper("TestRecord")
        mapper.assert(new("TestRecord", 42L), "{\"value\":42}")
        Assertions.assertThatThrownBy { mapper.read("{\"value\":null}") }.isInstanceOf(JsonParseException::class.java)
    }

    @Test
    fun testNullableLong() {
        compile("""
            @Json
            data class TestRecord(val value: Long?) {
            }
            
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
            data class TestRecord(val value: Short) {
            }
            
            """.trimIndent())
        compileResult.assertSuccess()
        val mapper = mapper("TestRecord")
        mapper.assert(new("TestRecord", 42.toShort()), "{\"value\":42}")
        Assertions.assertThatThrownBy { mapper.read("{\"value\":null}") }.isInstanceOf(JsonParseException::class.java)
    }

    @Test
    fun testNullableShort() {
        compile("""
            @Json
            data class TestRecord(val value: Short?) {
            }
            
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
            data class TestRecord(val value: Float) {
            }
            
            """.trimIndent())
        compileResult.assertSuccess()
        val mapper = mapper("TestRecord")
        mapper.assert(new("TestRecord", 42.1f), "{\"value\":42.1}")
        mapper.assertRead("{\"value\":42}", new("TestRecord", 42f))
        Assertions.assertThatThrownBy { mapper.read("{\"value\":null}") }.isInstanceOf(JsonParseException::class.java)
    }

    @Test
    fun testNullableFloat() {
        compile("""
            @Json
            data class TestRecord(val value: Float?) {
            }
            
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
            data class TestRecord(val value: Double) {
            }
            
            """.trimIndent())
        compileResult.assertSuccess()
        val mapper = mapper("TestRecord")
        mapper.assert(new("TestRecord", 42.1), "{\"value\":42.1}")
        mapper.assertRead("{\"value\":42}", new("TestRecord", 42.0))
        Assertions.assertThatThrownBy { mapper.read("{\"value\":null}") }.isInstanceOf(JsonParseException::class.java)
    }

    @Test
    fun testNullableDouble() {
        compile("""
            @Json
            data class TestRecord(val value: Double?) {
            }
            
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
            data class TestRecord(val value: Boolean) {
            }
            
            """.trimIndent())
        compileResult.assertSuccess()
        val mapper = mapper("TestRecord")
        mapper.assert(new("TestRecord", true), "{\"value\":true}")
        mapper.assert(new("TestRecord", false), "{\"value\":false}")
        Assertions.assertThatThrownBy { mapper.read("{\"value\":null}") }.isInstanceOf(JsonParseException::class.java)
    }

    @Test
    fun testNullableBoolean() {
        compile("""
            @Json
            data class TestRecord(val value: Boolean?) {
            }
            
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
            data class TestRecord(val value: String) {
            }
            
            """.trimIndent())
        compileResult.assertSuccess()
        val mapper = mapper("TestRecord")
        mapper.assert(new("TestRecord", "test"), "{\"value\":\"test\"}")
        Assertions.assertThatThrownBy { mapper.read("{\"value\":null}") }.isInstanceOf(JsonParseException::class.java)
    }

    @Test
    fun testNullableString() {
        compile("""
            @Json
            data class TestRecord(val value: String?) {
            }
            
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
            data class TestRecord(val value: java.util.UUID) {
            }
            
            """.trimIndent())
        compileResult.assertSuccess()
        val uuid = UUID.randomUUID()
        val mapper = mapper("TestRecord")
        mapper.assert(new("TestRecord", uuid), "{\"value\":\"$uuid\"}")
        Assertions.assertThatThrownBy { mapper.read("{\"value\":null}") }.isInstanceOf(JsonParseException::class.java)
    }

    @Test
    fun testNullableUuid() {
        compile("""
            @Json
            data class TestRecord(val value: java.util.UUID?) {
            }
            
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
            data class TestRecord(val value: java.math.BigInteger) {
            }
            
            """.trimIndent())
        compileResult.assertSuccess()
        val mapper = mapper("TestRecord")
        mapper.assert(new("TestRecord", BigInteger("42")), "{\"value\":42}")
        Assertions.assertThatThrownBy { mapper.read("{\"value\":null}") }.isInstanceOf(JsonParseException::class.java)
    }

    @Test
    fun testNullableBigInteger() {
        compile("""
            @Json
            data class TestRecord(val value: java.math.BigInteger?) {
            }
            
            """.trimIndent())
        compileResult.assertSuccess()
        val mapper = mapper("TestRecord")
        mapper.assert(new("TestRecord", BigInteger("42")), "{\"value\":42}")
        mapper.assert(new("TestRecord", null), "{}")
        mapper.assertRead("{\"value\":null}", new("TestRecord", null))
    }

    @Test
    fun testBigDecimal() {
        compile("""
            @Json
            data class TestRecord(val value: java.math.BigDecimal) {
            }
            
            """.trimIndent())
        compileResult.assertSuccess()
        val mapper = mapper("TestRecord")
        mapper.assert(new("TestRecord", BigDecimal("42")), "{\"value\":42}")
        mapper.assert(new("TestRecord", BigDecimal("42.43")), "{\"value\":42.43}")
        Assertions.assertThatThrownBy { mapper.read("{\"value\":null}") }.isInstanceOf(JsonParseException::class.java)
    }

    @Test
    fun testNullableBigDecimal() {
        compile("""
            @Json
            data class TestRecord(val value: java.math.BigDecimal?) {
            }
            
            """.trimIndent())
        compileResult.assertSuccess()
        val mapper = mapper("TestRecord")
        mapper.assert(new("TestRecord", BigDecimal("42")), "{\"value\":42}")
        mapper.assert(new("TestRecord", BigDecimal("42.43")), "{\"value\":42.43}")
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
        Assertions.assertThatThrownBy { mapper.read("{\"value\":null}") }.isInstanceOf(JsonParseException::class.java)
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
}
