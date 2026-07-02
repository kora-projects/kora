package io.koraframework.config.annotation.processor;

import io.koraframework.config.common.mapper.ConfigValueMapper;
import org.junit.jupiter.api.Test;
import io.koraframework.config.common.Config;
import io.koraframework.config.common.util.ConfigMappingUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class ConfigSourceAnnotationTest extends AbstractConfigTest {

    @Test
    public void testConfigSourceGeneratesConfigExtractor() {
        var mapper = this.compileConfig(List.of(), """
            @io.koraframework.config.common.annotation.ConfigSource("test.path")
            public interface TestConfig {
              int value();
            }
            """);

        assertThat(mapper.map(ConfigMappingUtils.fromMap(Map.of("value", 42)).root()))
            .isEqualTo(newObject("$TestConfig_ConfigValueMapper$TestConfig_Impl", 42));
    }

    @Test
    public void testConfigSourceGeneratesModule() throws NoSuchMethodException {
        this.compileConfig(List.of(), """
            @io.koraframework.config.common.annotation.ConfigSource("test.path")
            public interface TestConfig {
              int value();
            }
            """);

        var moduleClass = this.compileResult.loadClass("TestConfigModule");
        assertThat(moduleClass)
            .isNotNull()
            .isInterface()
            .hasMethods("testConfig");

        var method = moduleClass.getMethod("testConfig", Config.class, ConfigValueMapper.class);
        assertThat(method).isNotNull();
        assertThat(method.getReturnType()).isEqualTo(this.compileResult.loadClass("TestConfig"));
        assertThat(method.isDefault()).isTrue();
    }

}
