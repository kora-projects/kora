package io.koraframework.config.symbol.processor

import io.koraframework.config.common.ConfigValue.NullValue
import io.koraframework.config.common.ConfigValue.StringValue
import io.koraframework.config.common.exception.ConfigValueException
import io.koraframework.config.common.mapper.ConfigValueMapper
import io.koraframework.config.common.util.ConfigMappingUtils
import io.koraframework.validation.common.Validator
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import org.mockito.Mockito.verify
import org.mockito.invocation.InvocationOnMock
import org.mockito.kotlin.whenever
import java.time.Duration

class AnnotationConfigTest : AbstractConfigTest() {
    @Test
    fun testIntSupported() {
        val mapper = compileConfig(
            listOf<Any>(), """
            @ConfigMapper
            interface TestConfig {
              fun value() : Int
            }
            
            """.trimIndent()
        )
        assertThat(mapper.map(ConfigMappingUtils.fromMap(mapOf("value" to 42)).root()))
            .isEqualTo(new("\$TestConfig_ConfigValueMapper\$TestConfig_Impl", 42))
    }

    @Test
    fun testValSupported() {
        val mapper = compileConfig(
            listOf<Any>(), """
            @ConfigMapper
            interface TestConfig {
              val value: Int
            }
            
            """.trimIndent()
        )
        assertThat(mapper.map(ConfigMappingUtils.fromMap(mapOf("value" to 42)).root()))
            .isEqualTo(new("\$TestConfig_ConfigValueMapper\$TestConfig_Impl", 42))
    }

    @Test
    fun testNullableIntSupported() {
        val mapper = compileConfig(
            listOf<Any>(), """
            @ConfigMapper
            interface TestConfig {
              fun value() : Int?
            }
            """.trimIndent()
        )
        assertThat(mapper.map(ConfigMappingUtils.fromMap(mapOf("value" to 42)).root()))
            .isEqualTo(new("\$TestConfig_ConfigValueMapper\$TestConfig_Impl", 42))
        assertThat(mapper.map(ConfigMappingUtils.fromMap(mapOf<String, Any>()).root()))
            .isEqualTo(new("\$TestConfig_ConfigValueMapper\$TestConfig_Impl", null))
    }

    @Test
    fun testStringSupported() {
        val mapper = compileConfig(
            listOf<Any>(), """
            @ConfigMapper
            interface TestConfig {
              fun value(): String
            }
            """.trimIndent()
        )

        assertThat(mapper.map(ConfigMappingUtils.fromMap(mapOf("value" to "test")).root()))
            .isEqualTo(new("\$TestConfig_ConfigValueMapper\$TestConfig_Impl", "test"))

        assertThatThrownBy { mapper.map(ConfigMappingUtils.fromMap(mapOf<String, Any?>()).root()) }
            .isInstanceOf(ConfigValueException::class.java)
            .hasMessageStartingWith("Config expected value, but got null at path: 'ROOT.value' for origin");
    }

    @Test
    fun testValidInterfaceMapperValidatedAfterParse() {
        val validator = Mockito.mock(Validator::class.java) as Validator<Any?>
        val mapper = compileConfig(
            listOf(validator), """
            @ConfigMapper
            @io.koraframework.validation.common.annotation.Valid
            interface TestConfig {
              @io.koraframework.validation.common.annotation.NotBlank
              fun value(): String
            }
            """.trimIndent()
        )

        val result = mapper.map(ConfigMappingUtils.fromMap(mapOf("value" to "test")).root())

        assertThat(result)
            .isEqualTo(new("\$TestConfig_ConfigValueMapper\$TestConfig_Impl", "test"))
        verify(validator).validateAndThrow(result)
    }

    @Test
    fun testBooleanSupported() {
        val mapper = compileConfig(
            listOf<Any>(), """
            @ConfigMapper
            interface TestConfig {
              fun value(): Boolean 
            }
            """.trimIndent()
        )

        assertThat(mapper.map(ConfigMappingUtils.fromMap(mapOf("value" to true)).root()))
            .isEqualTo(new("\$TestConfig_ConfigValueMapper\$TestConfig_Impl", true))

        assertThatThrownBy { mapper.map(ConfigMappingUtils.fromMap(mapOf<String, Any?>()).root()) }
            .isInstanceOf(ConfigValueException::class.java)
            .hasMessageStartingWith("Config expected value, but got null at path: 'ROOT.value' for origin");
    }

    @Test
    fun testLongSupported() {
        val mapper = compileConfig(
            listOf<Any>(), """
            @ConfigMapper
            interface TestConfig {
              fun value(): Long 
            }
            """.trimIndent()
        )

        assertThat(mapper.map(ConfigMappingUtils.fromMap(mapOf("value" to 42L)).root()))
            .isEqualTo(new("\$TestConfig_ConfigValueMapper\$TestConfig_Impl", 42L))

        assertThatThrownBy { mapper.map(ConfigMappingUtils.fromMap(mapOf<String, Any?>()).root()) }
            .isInstanceOf(ConfigValueException::class.java)
            .hasMessageStartingWith("Config expected value, but got null at path: 'ROOT.value' for origin");
    }

    @Test
    fun testDoubleSupported() {
        val mapper = compileConfig(
            listOf<Any>(), """
            @ConfigMapper
            interface TestConfig {
              fun value(): Double
            }
            """.trimIndent()
        )

        assertThat(mapper.map(ConfigMappingUtils.fromMap(mapOf("value" to 1.0)).root()))
            .isEqualTo(new("\$TestConfig_ConfigValueMapper\$TestConfig_Impl", 1.0))

        assertThatThrownBy { mapper.map(ConfigMappingUtils.fromMap(mapOf<String, Any?>()).root()) }
            .isInstanceOf(ConfigValueException::class.java)
            .hasMessageStartingWith("Config expected value, but got null at path: 'ROOT.value' for origin");
    }

    @Test
    fun testDefaultValues() {
        val mapper = compileConfig(
            listOf<Any>(), """
            @ConfigMapper
            interface TestConfig {
              fun value() = "default-value"
            }
            
            """.trimIndent()
        )

        assertThat(mapper.map(ConfigMappingUtils.fromMap(mapOf("value" to "test")).root()))
            .isEqualTo(new("\$TestConfig_ConfigValueMapper\$TestConfig_Impl", "test"))

        assertThat(mapper.map(ConfigMappingUtils.fromMap(mapOf<String, Any?>()).root()))
            .isEqualTo(new("\$TestConfig_ConfigValueMapper\$TestConfig_Impl", "default-value"))
    }

    @Test
    fun testDefaultAndNullable() {
        val mapper = compileConfig(
            listOf<Any>(), """
            @ConfigMapper
            interface TestConfig {
              fun value1() = "default-value"
              
              fun value2(): String?
            }
            
            """.trimIndent()
        )

        assertThat(mapper.map(ConfigMappingUtils.fromMap(mapOf("value1" to "test")).root()))
            .isEqualTo(new("\$TestConfig_ConfigValueMapper\$TestConfig_Impl", "test", null))
        assertThat(mapper.map(ConfigMappingUtils.fromMap(mapOf<String, Any?>()).root()))
            .isEqualTo(new("\$TestConfig_ConfigValueMapper\$TestConfig_Impl", "default-value", null))
    }

    @Test
    fun testInterfaceWithUnknownType() {
        val mockMapper = Mockito.mock(ConfigValueMapper::class.java)
        whenever(mockMapper.map(ArgumentMatchers.isA(StringValue::class.java))).thenReturn(Duration.ofDays(3000))
        whenever(mockMapper.map(ArgumentMatchers.isA(NullValue::class.java))).thenThrow(IllegalArgumentException())

        val mapper = compileConfig(
            listOf(mockMapper), """
            @ConfigMapper
            interface TestConfig {
              fun value1(): java.time.Duration
              fun value2(): java.time.Duration?
            }
            
            """.trimIndent()
        )

        assertThat(mapper.map(ConfigMappingUtils.fromMap(mapOf("value1" to "test")).root()))
            .isEqualTo(new("\$TestConfig_ConfigValueMapper\$TestConfig_Impl", Duration.ofDays(3000), null))
        verify(mockMapper).map(ArgumentMatchers.any())
    }

    @Test
    fun testInterfaceWithDefaultUnknownType() {
        val mockMapper = Mockito.mock(ConfigValueMapper::class.java)
        whenever(mockMapper.map(ArgumentMatchers.isA(StringValue::class.java))).thenReturn(Duration.ofDays(3000))
        whenever(mockMapper.map(ArgumentMatchers.isA(NullValue::class.java))).thenThrow(IllegalArgumentException())

        val mapper = compileConfig(
            listOf(mockMapper), """
            @ConfigMapper
            interface TestConfig {
              fun value(): java.time.Duration = java.time.Duration.ofDays(1)
            }

            """.trimIndent()
        )

        assertThat(mapper.map(ConfigMappingUtils.fromMap(mapOf("value" to "test")).root()))
            .isEqualTo(new("\$TestConfig_ConfigValueMapper\$TestConfig_Impl", Duration.ofDays(3000)))
        assertThat(mapper.map(ConfigMappingUtils.fromMap(mapOf<String, Any>()).root()))
            .isEqualTo(new("\$TestConfig_ConfigValueMapper\$TestConfig_Impl", Duration.ofDays(1)))
    }

    @Test
    fun testInterfaceWithDefaultAndRequiredUnknownType() {
        val mockMapper = Mockito.mock(ConfigValueMapper::class.java)
        whenever(mockMapper.map(ArgumentMatchers.isA(StringValue::class.java))).thenReturn(Duration.ofDays(3000))
        whenever(mockMapper.map(ArgumentMatchers.isA(NullValue::class.java))).thenThrow(IllegalArgumentException())

        val mapper = compileConfig(
            listOf(mockMapper), """
            @ConfigMapper
            interface TestConfig {
              fun value1(): java.time.Duration
              fun value2(): java.time.Duration = java.time.Duration.ofDays(1)
            }

            """.trimIndent()
        )

        assertThat(mapper.map(ConfigMappingUtils.fromMap(mapOf("value1" to "test", "value2" to "test")).root()))
            .isEqualTo(new("\$TestConfig_ConfigValueMapper\$TestConfig_Impl", Duration.ofDays(3000), Duration.ofDays(3000)))
        assertThat(mapper.map(ConfigMappingUtils.fromMap(mapOf("value1" to "test")).root()))
            .isEqualTo(new("\$TestConfig_ConfigValueMapper\$TestConfig_Impl", Duration.ofDays(3000), Duration.ofDays(1)))
    }

    @Test
    fun testDataClassWithDefaultUnknownType() {
        val mockMapper = Mockito.mock(ConfigValueMapper::class.java)
        whenever(mockMapper.map(ArgumentMatchers.isA(StringValue::class.java))).thenReturn(Duration.ofDays(3000))
        whenever(mockMapper.map(ArgumentMatchers.isA(NullValue::class.java))).thenThrow(IllegalArgumentException())

        val mapper = compileConfig(
            listOf(mockMapper), """
            @ConfigMapper
            data class TestConfig(val value1: String, val value2: java.time.Duration = java.time.Duration.ofDays(1))
            """.trimIndent()
        )

        assertThat(mapper.map(ConfigMappingUtils.fromMap(mapOf("value1" to "test1", "value2" to "test2")).root()))
            .isEqualTo(new("TestConfig", "test1", Duration.ofDays(3000)))
        assertThat(mapper.map(ConfigMappingUtils.fromMap(mapOf("value1" to "test1")).root()))
            .isEqualTo(new("TestConfig", "test1", Duration.ofDays(1)))
    }

    @Test
    fun testInterfaceWithNullableDefaultUnknownType() {
        val mockMapper = Mockito.mock(ConfigValueMapper::class.java)
        whenever(mockMapper.map(ArgumentMatchers.isA(StringValue::class.java))).thenReturn(Duration.ofDays(3000))
        whenever(mockMapper.map(ArgumentMatchers.isA(NullValue::class.java))).thenThrow(IllegalArgumentException())

        val mapper = compileConfig(
            listOf(mockMapper), """
            @ConfigMapper
            interface TestConfig {
              fun value(): java.time.Duration? = java.time.Duration.ofDays(1)
            }

            """.trimIndent()
        )

        assertThat(mapper.map(ConfigMappingUtils.fromMap(mapOf("value" to "test")).root()))
            .isEqualTo(new("\$TestConfig_ConfigValueMapper\$TestConfig_Impl", Duration.ofDays(3000)))
        assertThat(mapper.map(ConfigMappingUtils.fromMap(mapOf<String, Any>()).root()))
            .isEqualTo(new("\$TestConfig_ConfigValueMapper\$TestConfig_Impl", Duration.ofDays(1)))
    }

    @Test
    fun testInterfaceWithSuperDefaultUnknownType() {
        val mockMapper = Mockito.mock(ConfigValueMapper::class.java)
        whenever(mockMapper.map(ArgumentMatchers.isA(StringValue::class.java))).thenReturn(Duration.ofDays(3000))
        whenever(mockMapper.map(ArgumentMatchers.isA(NullValue::class.java))).thenThrow(IllegalArgumentException())

        val mapper = compileConfig(
            listOf(mockMapper), """
            @ConfigMapper
            interface TestConfig : SuperTestConfig {
              fun value1(): String
            }

            """.trimIndent(), """
            interface SuperTestConfig {
              fun value2(): java.time.Duration = java.time.Duration.ofDays(1)
            }

            """.trimIndent()
        )

        assertThat(mapper.map(ConfigMappingUtils.fromMap(mapOf("value1" to "test1", "value2" to "test2")).root()))
            .isEqualTo(new("\$TestConfig_ConfigValueMapper\$TestConfig_Impl", "test1", Duration.ofDays(3000)))
        assertThat(mapper.map(ConfigMappingUtils.fromMap(mapOf("value1" to "test1")).root()))
            .isEqualTo(new("\$TestConfig_ConfigValueMapper\$TestConfig_Impl", "test1", Duration.ofDays(1)))
    }

    @Test
    fun testInterfaceWithSuper() {
        val mapper = compileConfig(
            listOf<Any>(), """
            @ConfigMapper
            interface TestConfig : SuperTestConfig {
              fun value1(): String
            }
            
            """.trimIndent(), """
            interface SuperTestConfig {
              fun value2() = "default-value"
            }
            
            """.trimIndent()
        )

        assertThat(mapper.map(ConfigMappingUtils.fromMap(mapOf("value1" to "test1", "value2" to "test2")).root()))
            .isEqualTo(new("\$TestConfig_ConfigValueMapper\$TestConfig_Impl", "test1", "test2"))
    }

    @Test
    fun testInterfaceWithArray() {
        val mockMapper = Mockito.mock(
            ConfigValueMapper::class.java
        )
        whenever(mockMapper.map(ArgumentMatchers.any()))
            .thenAnswer { invocation: InvocationOnMock? -> intArrayOf(1, 2, 3) }

        val mapper = compileConfig(
            listOf(mockMapper), """
            @ConfigMapper
            interface TestConfig {
              fun value(): IntArray
            }
            
            """.trimIndent()
        )

        assertThat(mapper.map(ConfigMappingUtils.fromMap(mapOf("value" to "test")).root()))
            .isEqualTo(new("\$TestConfig_ConfigValueMapper\$TestConfig_Impl", intArrayOf(1, 2, 3) as Any))
        verify(mockMapper).map(ArgumentMatchers.any())
    }

    @Test
    fun testDataClass() {
        val mapper = compileConfig(
            listOf<Any>(), """
            @ConfigMapper
            data class TestConfig(val value: String)
            """.trimIndent()
        )

        assertThat(mapper.map(ConfigMappingUtils.fromMap(mapOf("value" to "test")).root()))
            .isEqualTo(new("TestConfig", "test"))
    }

    @Test
    fun testDataClassAllNullable() {
        val mapper = compileConfig(
            listOf<Any>(), """
            @ConfigMapper
            data class TestConfig(val value: String?)
            """.trimIndent()
        )

        assertThat(mapper.map(ConfigMappingUtils.fromMap(mapOf("value" to "test")).root()))
            .isEqualTo(new("TestConfig", "test"))
    }

    @Test
    fun testDataClassWithUnknownType() {
        val mockMapper = Mockito.mock(ConfigValueMapper::class.java)
        whenever(mockMapper.map(ArgumentMatchers.isA(StringValue::class.java))).thenReturn(Duration.ofDays(3000))
        whenever(mockMapper.map(ArgumentMatchers.isA(NullValue::class.java))).thenThrow(IllegalArgumentException())

        val mapper = compileConfig(
            listOf(mockMapper), """
            @ConfigMapper
            data class TestConfig(val value1: java.time.Duration, val value2: java.time.Duration?)
            """.trimIndent()
        )

        assertThat(mapper.map(ConfigMappingUtils.fromMap(mapOf("value1" to "test")).root()))
            .isEqualTo(new("TestConfig", Duration.ofDays(3000), null))
        verify(mockMapper).map(ArgumentMatchers.any())
    }

    @Test
    fun testDataClassWithUnknownTypeAndMapping() {
        val mapper = compileConfig(
            listOf(newGenerated("TestOpenExtractor")), """
            @ConfigMapper
            data class TestConfig(@Mapping(TestOpenExtractor::class) @Tag(TestOpenExtractor::class) val value1: java.time.Duration, @Mapping(TestExtractor::class) val value2: java.time.Duration?)
            """.trimIndent(), """
            import io.koraframework.config.common.ConfigValue
            open class TestOpenExtractor : io.koraframework.config.common.mapper.ConfigValueMapper<java.time.Duration> {
              override fun map(value: ConfigValue<*>): java.time.Duration? {
                return if (value is ConfigValue.NullValue) null else java.time.Duration.ofDays(3000)
              }
            }
            """.trimIndent(), """
            import io.koraframework.config.common.ConfigValue
            class TestExtractor : io.koraframework.config.common.mapper.ConfigValueMapper<java.time.Duration> {
              override fun map(value: ConfigValue<*>): java.time.Duration? {
                return if (value is ConfigValue.NullValue) null else java.time.Duration.ofDays(3000)
              }
            }
            """.trimIndent()
        )

        assertThat(mapper.map(ConfigMappingUtils.fromMap(mapOf("value1" to "test")).root()))
            .isEqualTo(new("TestConfig", Duration.ofDays(3000), null))
    }

    @Test
    fun testDataClassWithDefaultValue() {
        val mapper = compileConfig(
            listOf<Any>(), """
            @ConfigMapper
            data class TestConfig(val value1: String, val value2: String = "default-value")
            """.trimIndent()
        )

        assertThat(mapper.map(ConfigMappingUtils.fromMap(mapOf("value1" to "test1", "value2" to "test2")).root()))
            .isEqualTo(new("TestConfig", "test1", "test2"))
        assertThat(mapper.map(ConfigMappingUtils.fromMap(mapOf("value1" to "test1")).root()))
            .isEqualTo(new("TestConfig", "test1", "default-value"))
    }

    @Test
    fun testDataClassWithAllDefaults() {
        val mapper = compileConfig(
            listOf<Any>(), """
            @ConfigMapper
            data class TestConfig(val value1: String = "default1", val value2: Int = 42)
            """.trimIndent()
        )

        assertThat(mapper.map(ConfigMappingUtils.fromMap(mapOf("value1" to "test1", "value2" to 100)).root()).toString())
            .isEqualTo("TestConfig(value1=test1, value2=100)")
        assertThat(mapper.map(ConfigMappingUtils.fromMap(mapOf("value1" to "test1")).root()).toString())
            .isEqualTo("TestConfig(value1=test1, value2=42)")
        assertThat(mapper.map(ConfigMappingUtils.fromMap(mapOf("value2" to 100)).root()).toString())
            .isEqualTo("TestConfig(value1=default1, value2=100)")
        assertThat(mapper.map(ConfigMappingUtils.fromMap(mapOf<String, Any>()).root()).toString())
            .isEqualTo("TestConfig(value1=default1, value2=42)")
    }

    @Test
    fun testDataClassWithDefaultAndNullable() {
        val mapper = compileConfig(
            listOf<Any>(), """
            @ConfigMapper
            data class TestConfig(val value1: String, val value2: String? = "default-value")
            """.trimIndent()
        )

        assertThat(mapper.map(ConfigMappingUtils.fromMap(mapOf("value1" to "test1", "value2" to "test2")).root()))
            .isEqualTo(new("TestConfig", "test1", "test2"))
        assertThat(mapper.map(ConfigMappingUtils.fromMap(mapOf("value1" to "test1")).root()))
            .isEqualTo(new("TestConfig", "test1", "default-value"))
    }

    @Test
    fun testDataClassWithMultipleDefaults() {
        val mapper = compileConfig(
            listOf<Any>(), """
            @ConfigMapper
            data class TestConfig(val value1: String, val value2: String = "default2", val value3: Int = 42, val value4: Boolean = true)
            """.trimIndent()
        )

        assertThat(mapper.map(ConfigMappingUtils.fromMap(mapOf("value1" to "test1")).root()))
            .isEqualTo(new("TestConfig", "test1", "default2", 42, true))
        assertThat(mapper.map(ConfigMappingUtils.fromMap(mapOf("value1" to "test1", "value2" to "v2", "value3" to 100, "value4" to false)).root()))
            .isEqualTo(new("TestConfig", "test1", "v2", 100, false))
        assertThat(mapper.map(ConfigMappingUtils.fromMap(mapOf("value1" to "test1", "value3" to 100)).root()))
            .isEqualTo(new("TestConfig", "test1", "default2", 100, true))
    }

    @Test
    fun testDataClassWithAllDefaultUnknownTypes() {
        val mockMapper = Mockito.mock(ConfigValueMapper::class.java)
        whenever(mockMapper.map(ArgumentMatchers.isA(StringValue::class.java))).thenReturn(Duration.ofDays(3000))
        whenever(mockMapper.map(ArgumentMatchers.isA(NullValue::class.java))).thenThrow(IllegalArgumentException())

        val mapper = compileConfig(
            listOf(mockMapper), """
            @ConfigMapper
            data class TestConfig(val value1: java.time.Duration = java.time.Duration.ofDays(1), val value2: java.time.Duration = java.time.Duration.ofDays(2))
            """.trimIndent()
        )

        assertThat(mapper.map(ConfigMappingUtils.fromMap(mapOf("value1" to "test", "value2" to "test")).root()).toString())
            .isEqualTo("TestConfig(value1=PT72000H, value2=PT72000H)")
        assertThat(mapper.map(ConfigMappingUtils.fromMap(mapOf("value1" to "test")).root()).toString())
            .isEqualTo("TestConfig(value1=PT72000H, value2=PT48H)")
        assertThat(mapper.map(ConfigMappingUtils.fromMap(mapOf<String, Any>()).root()).toString())
            .isEqualTo("TestConfig(value1=PT24H, value2=PT48H)")
    }

    @Test
    fun testDataClassWithNullableDefaultUnknownType() {
        val mockMapper = Mockito.mock(ConfigValueMapper::class.java)
        whenever(mockMapper.map(ArgumentMatchers.isA(StringValue::class.java))).thenReturn(Duration.ofDays(3000))
        whenever(mockMapper.map(ArgumentMatchers.isA(NullValue::class.java))).thenThrow(IllegalArgumentException())

        val mapper = compileConfig(
            listOf(mockMapper), """
            @ConfigMapper
            data class TestConfig(val value1: String, val value2: java.time.Duration? = java.time.Duration.ofDays(1))
            """.trimIndent()
        )

        assertThat(mapper.map(ConfigMappingUtils.fromMap(mapOf("value1" to "test1", "value2" to "test2")).root()))
            .isEqualTo(new("TestConfig", "test1", Duration.ofDays(3000)))
        assertThat(mapper.map(ConfigMappingUtils.fromMap(mapOf("value1" to "test1")).root()))
            .isEqualTo(new("TestConfig", "test1", Duration.ofDays(1)))
    }

    @Test
    fun testDataClassWithDefaultAndCustomType() {
        val mockMapper = Mockito.mock(ConfigValueMapper::class.java)
        whenever(mockMapper.map(ArgumentMatchers.isA(StringValue::class.java))).thenReturn(Duration.ofDays(3000))

        val mapper = compileConfig(
            listOf(mockMapper), """
            @ConfigMapper
            data class TestConfig(val value1: java.time.Duration, val value2: String = "default-value")
            """.trimIndent()
        )

        assertThat(mapper.map(ConfigMappingUtils.fromMap(mapOf("value1" to "test")).root()).toString())
            .isEqualTo("TestConfig(value1=PT72000H, value2=default-value)")
        assertThat(mapper.map(ConfigMappingUtils.fromMap(mapOf("value1" to "test", "value2" to "custom")).root()).toString())
            .isEqualTo("TestConfig(value1=PT72000H, value2=custom)")
    }

    @Test
    fun testEmptyConfig() {
        val mapper = compileConfig(
            listOf<Any>(), """
            @ConfigMapper
            interface TestConfig
            
            """.trimIndent()
        )
        val instance1 = mapper.map(ConfigMappingUtils.fromMap(mapOf("value" to 42)).root())
        val instance2 = mapper.map(ConfigMappingUtils.fromMap(mapOf<String, Any>()).root())
        assertThat(instance1)
            .isNotNull()
            .isEqualTo(instance2)
    }

}
