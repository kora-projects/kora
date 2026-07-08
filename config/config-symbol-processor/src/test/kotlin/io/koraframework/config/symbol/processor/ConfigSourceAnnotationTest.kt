package io.koraframework.config.symbol.processor

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import io.koraframework.config.common.Config
import io.koraframework.config.common.mapper.ConfigValueMapper
import io.koraframework.config.common.util.ConfigMappingUtils

class ConfigSourceAnnotationTest : AbstractConfigTest() {
    @Test
    fun testConfigSourceGeneratesConfigExtractor() {
        val mapper = compileConfig(
            listOf<Any>(), """
            @ConfigSource("test.path")
            interface TestConfig {
              fun value() : Int
            }
            
            """.trimIndent()
        )
        assertThat(mapper.map(ConfigMappingUtils.fromMap(mapOf("value" to 42)).root()))
            .isEqualTo(new($$"$TestConfig_ConfigValueMapper$TestConfig_Impl", 42))
    }

    @Test
    fun testConfigSourceGeneratesModule() {
        compileConfig(
            listOf<Any>(), """
            @ConfigSource("test.path")
            interface TestConfig {
              fun value() : Int
            }
            
            """.trimIndent()
        )
        val moduleClass = loadClass("TestConfigModule")
        assertThat(moduleClass)
            .isNotNull()
            .isInterface()
            .hasMethods("testConfig")

        val method = moduleClass.getMethod("testConfig", Config::class.java, ConfigValueMapper::class.java)
        assertThat(method).isNotNull()
        assertThat(method.returnType).isEqualTo(loadClass("TestConfig"))
        assertThat(method.isDefault).isTrue()
    }
}
