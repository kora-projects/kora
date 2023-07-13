package ru.tinkoff.kora.config.symbol.processor

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.tinkoff.kora.config.common.Config
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor
import ru.tinkoff.kora.config.common.factory.MapConfigFactory

class ConfigSourceAnnotationTest : AbstractConfigTest() {
    @Test
    fun testConfigSourceGeneratesConfigExtractor() {
        val extractor = compileConfig(
            listOf<Any>(), """
            @ConfigSource("test.path")
            interface TestConfig {
              fun value() : Int
            }
            
            """.trimIndent()
        )
        assertThat(extractor!!.extract(MapConfigFactory.fromMap(mapOf("value" to 42)).root()))
            .isEqualTo(new("\$TestConfig_ConfigValueExtractor\$TestConfig_Impl", 42))
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
        val moduleClass = compileResult.loadClass("TestConfigModule")
        assertThat(moduleClass)
            .isNotNull()
            .isInterface()
            .hasMethods("testConfig")

        val method = moduleClass.getMethod("testConfig", Config::class.java, ConfigValueExtractor::class.java)
        assertThat(method).isNotNull()
        assertThat(method.returnType).isEqualTo(compileResult.loadClass("TestConfig"))
        assertThat(method.isDefault).isTrue()
    }
}
