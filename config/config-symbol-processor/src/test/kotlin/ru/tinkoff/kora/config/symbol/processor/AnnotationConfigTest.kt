package ru.tinkoff.kora.config.symbol.processor

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import org.mockito.Mockito.verify
import org.mockito.invocation.InvocationOnMock
import org.mockito.kotlin.whenever
import ru.tinkoff.kora.config.common.ConfigValue.NullValue
import ru.tinkoff.kora.config.common.ConfigValue.StringValue
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractionException
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor
import ru.tinkoff.kora.config.common.factory.MapConfigFactory
import java.time.Duration

class AnnotationConfigTest : AbstractConfigTest() {
    @Test
    fun testIntSupported() {
        val extractor = compileConfig(
            listOf<Any>(), """
            @ConfigValueExtractor
            interface TestConfig {
              fun value() : Int
            }
            
            """.trimIndent()
        )
        assertThat(extractor.extract(MapConfigFactory.fromMap(mapOf("value" to 42)).root()))
            .isEqualTo(new("\$TestConfig_ConfigValueExtractor\$TestConfig_Impl", 42))
    }

    @Test
    fun testValSupported() {
        val extractor = compileConfig(
            listOf<Any>(), """
            @ConfigValueExtractor
            interface TestConfig {
              val value: Int
            }
            
            """.trimIndent()
        )
        assertThat(extractor.extract(MapConfigFactory.fromMap(mapOf("value" to 42)).root()))
            .isEqualTo(new("\$TestConfig_ConfigValueExtractor\$TestConfig_Impl", 42))
    }

    @Test
    fun testNullableIntSupported() {
        val extractor = compileConfig(
            listOf<Any>(), """
            @ConfigValueExtractor
            interface TestConfig {
              fun value() : Int?
            }
            """.trimIndent()
        )
        assertThat(extractor.extract(MapConfigFactory.fromMap(mapOf("value" to 42)).root()))
            .isEqualTo(new("\$TestConfig_ConfigValueExtractor\$TestConfig_Impl", 42))
        assertThat(extractor.extract(MapConfigFactory.fromMap(mapOf<String, Any>()).root()))
            .isEqualTo(new("\$TestConfig_ConfigValueExtractor\$TestConfig_Impl", null))
    }

    @Test
    fun testStringSupported() {
        val extractor = compileConfig(
            listOf<Any>(), """
            @ConfigValueExtractor
            interface TestConfig {
              fun value(): String
            }
            """.trimIndent()
        )

        assertThat(extractor.extract(MapConfigFactory.fromMap(mapOf("value" to "test")).root()))
            .isEqualTo(new("\$TestConfig_ConfigValueExtractor\$TestConfig_Impl", "test"))

        assertThatThrownBy { extractor.extract(MapConfigFactory.fromMap(mapOf<String, Any?>()).root()) }
            .isInstanceOf(ConfigValueExtractionException::class.java)
            .hasMessageStartingWith("Config expected value, but got null at path: 'ROOT.value' for origin");
    }

    @Test
    fun testBooleanSupported() {
        val extractor = compileConfig(
            listOf<Any>(), """
            @ConfigValueExtractor
            interface TestConfig {
              fun value(): Boolean 
            }
            """.trimIndent()
        )

        assertThat(extractor.extract(MapConfigFactory.fromMap(mapOf("value" to true)).root()))
            .isEqualTo(new("\$TestConfig_ConfigValueExtractor\$TestConfig_Impl", true))

        assertThatThrownBy { extractor.extract(MapConfigFactory.fromMap(mapOf<String, Any?>()).root()) }
            .isInstanceOf(ConfigValueExtractionException::class.java)
            .hasMessageStartingWith("Config expected value, but got null at path: 'ROOT.value' for origin");
    }

    @Test
    fun testLongSupported() {
        val extractor = compileConfig(
            listOf<Any>(), """
            @ConfigValueExtractor
            interface TestConfig {
              fun value(): Long 
            }
            """.trimIndent()
        )

        assertThat(extractor.extract(MapConfigFactory.fromMap(mapOf("value" to 42L)).root()))
            .isEqualTo(new("\$TestConfig_ConfigValueExtractor\$TestConfig_Impl", 42L))

        assertThatThrownBy { extractor.extract(MapConfigFactory.fromMap(mapOf<String, Any?>()).root()) }
            .isInstanceOf(ConfigValueExtractionException::class.java)
            .hasMessageStartingWith("Config expected value, but got null at path: 'ROOT.value' for origin");
    }

    @Test
    fun testDoubleSupported() {
        val extractor = compileConfig(
            listOf<Any>(), """
            @ConfigValueExtractor
            interface TestConfig {
              fun value(): Double
            }
            """.trimIndent()
        )

        assertThat(extractor.extract(MapConfigFactory.fromMap(mapOf("value" to 1.0)).root()))
            .isEqualTo(new("\$TestConfig_ConfigValueExtractor\$TestConfig_Impl", 1.0))

        assertThatThrownBy { extractor.extract(MapConfigFactory.fromMap(mapOf<String, Any?>()).root()) }
            .isInstanceOf(ConfigValueExtractionException::class.java)
            .hasMessageStartingWith("Config expected value, but got null at path: 'ROOT.value' for origin");
    }

    @Test
    fun testDefaultValues() {
        val extractor = compileConfig(
            listOf<Any>(), """
            @ConfigValueExtractor
            interface TestConfig {
              fun value() = "default-value"
            }
            
            """.trimIndent()
        )

        assertThat(extractor.extract(MapConfigFactory.fromMap(mapOf("value" to "test")).root()))
            .isEqualTo(new("\$TestConfig_ConfigValueExtractor\$TestConfig_Impl", "test"))

        assertThat(extractor.extract(MapConfigFactory.fromMap(mapOf<String, Any?>()).root()))
            .isEqualTo(new("\$TestConfig_ConfigValueExtractor\$TestConfig_Impl", "default-value"))
    }

    @Test
    fun testDefaultAndNullable() {
        val extractor = compileConfig(
            listOf<Any>(), """
            @ConfigValueExtractor
            interface TestConfig {
              fun value1() = "default-value"
              
              fun value2(): String?
            }
            
            """.trimIndent()
        )

        assertThat(extractor.extract(MapConfigFactory.fromMap(mapOf("value1" to "test")).root()))
            .isEqualTo(new("\$TestConfig_ConfigValueExtractor\$TestConfig_Impl", "test", null))
        assertThat(extractor.extract(MapConfigFactory.fromMap(mapOf<String, Any?>()).root()))
            .isEqualTo(new("\$TestConfig_ConfigValueExtractor\$TestConfig_Impl", "default-value", null))
    }

    @Test
    fun testInterfaceWithUnknownType() {
        val mapper = Mockito.mock(ConfigValueExtractor::class.java)
        whenever(mapper.extract(ArgumentMatchers.isA(StringValue::class.java))).thenReturn(Duration.ofDays(3000))
        whenever(mapper.extract(ArgumentMatchers.isA(NullValue::class.java))).thenThrow(IllegalArgumentException())

        val extractor = compileConfig(
            listOf(mapper), """
            @ConfigValueExtractor
            interface TestConfig {
              fun value1(): java.time.Duration
              fun value2(): java.time.Duration?
            }
            
            """.trimIndent()
        )

        assertThat(extractor.extract(MapConfigFactory.fromMap(mapOf("value1" to "test")).root()))
            .isEqualTo(new("\$TestConfig_ConfigValueExtractor\$TestConfig_Impl", Duration.ofDays(3000), null))
        verify(mapper).extract(ArgumentMatchers.any())
    }

    @Test
    fun testInterfaceWithSuper() {
        val extractor = compileConfig(
            listOf<Any>(), """
            @ConfigValueExtractor
            interface TestConfig : SuperTestConfig {
              fun value1(): String
            }
            
            """.trimIndent(), """
            interface SuperTestConfig {
              fun value2() = "default-value"
            }
            
            """.trimIndent()
        )

        assertThat(extractor.extract(MapConfigFactory.fromMap(mapOf("value1" to "test1", "value2" to "test2")).root()))
            .isEqualTo(new("\$TestConfig_ConfigValueExtractor\$TestConfig_Impl", "test1", "test2"))
    }

    @Test
    fun testInterfaceWithArray() {
        val mapper = Mockito.mock(
            ConfigValueExtractor::class.java
        )
        whenever(mapper.extract(ArgumentMatchers.any()))
            .thenAnswer { invocation: InvocationOnMock? -> intArrayOf(1, 2, 3) }

        val extractor = compileConfig(
            listOf(mapper), """
            @ConfigValueExtractor
            interface TestConfig {
              fun value(): IntArray
            }
            
            """.trimIndent()
        )

        assertThat(extractor.extract(MapConfigFactory.fromMap(mapOf("value" to "test")).root()))
            .isEqualTo(new("\$TestConfig_ConfigValueExtractor\$TestConfig_Impl", intArrayOf(1, 2, 3) as Any))
        verify(mapper).extract(ArgumentMatchers.any())
    }

    @Test
    fun testDataClass() {
        val extractor = compileConfig(
            listOf<Any>(), """
            @ConfigValueExtractor
            data class TestConfig(val value: String)
            """.trimIndent()
        )

        assertThat(extractor.extract(MapConfigFactory.fromMap(mapOf("value" to "test")).root()))
            .isEqualTo(new("TestConfig", "test"))
    }

    @Test
    fun testDataClassAllNullable() {
        val extractor = compileConfig(
            listOf<Any>(), """
            @ConfigValueExtractor
            data class TestConfig(val value: String?)
            """.trimIndent()
        )

        assertThat(extractor.extract(MapConfigFactory.fromMap(mapOf("value" to "test")).root()))
            .isEqualTo(new("TestConfig", "test"))
    }

    @Test
    fun testDataClassWithUnknownType() {
        val mapper = Mockito.mock(ConfigValueExtractor::class.java)
        whenever(mapper.extract(ArgumentMatchers.isA(StringValue::class.java))).thenReturn(Duration.ofDays(3000))
        whenever(mapper.extract(ArgumentMatchers.isA(NullValue::class.java))).thenThrow(IllegalArgumentException())

        val extractor = compileConfig(
            listOf(mapper), """
            @ConfigValueExtractor
            data class TestConfig(val value1: java.time.Duration, val value2: java.time.Duration?)
            """.trimIndent()
        )

        assertThat(extractor.extract(MapConfigFactory.fromMap(mapOf("value1" to "test")).root()))
            .isEqualTo(new("TestConfig", Duration.ofDays(3000), null))
        verify(mapper).extract(ArgumentMatchers.any())
    }

    @Test
    fun testDataClassWithUnknownTypeAndMapping() {
        val extractor = compileConfig(
            listOf(newGenerated("TestOpenExtractor")), """
            @ConfigValueExtractor
            data class TestConfig(@Mapping(TestOpenExtractor::class) @Tag(TestOpenExtractor::class) val value1: java.time.Duration, @Mapping(TestExtractor::class) val value2: java.time.Duration?)
            """.trimIndent(), """
            import ru.tinkoff.kora.config.common.ConfigValue
            open class TestOpenExtractor : ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor<java.time.Duration> {
              override fun extract(value: ConfigValue<*>): java.time.Duration? {
                if (value is ConfigValue.NullValue)
                  return null
                else
                  return java.time.Duration.ofDays(3000)
              }

            }
            """.trimIndent(), """
            import ru.tinkoff.kora.config.common.ConfigValue
            
            class TestExtractor : ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor<java.time.Duration> {
              override fun extract(value: ConfigValue<*>): java.time.Duration? {
                if (value is ConfigValue.NullValue)
                  return null
                else
                  return java.time.Duration.ofDays(3000)
              }
            }
            """.trimIndent()
        )

        assertThat(extractor.extract(MapConfigFactory.fromMap(mapOf("value1" to "test")).root()))
            .isEqualTo(new("TestConfig", Duration.ofDays(3000), null))
    }

    @Test
    fun testEmptyConfig() {
        val extractor = compileConfig(
            listOf<Any>(), """
            @ConfigValueExtractor
            interface TestConfig
            
            """.trimIndent()
        )
        val instance1 = extractor.extract(MapConfigFactory.fromMap(mapOf("value" to 42)).root())
        val instance2 = extractor.extract(MapConfigFactory.fromMap(mapOf<String, Any>()).root())
        assertThat(instance1)
            .isNotNull()
            .isEqualTo(instance2)
    }

}
