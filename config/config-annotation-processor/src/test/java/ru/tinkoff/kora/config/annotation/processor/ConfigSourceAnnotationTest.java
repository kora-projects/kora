package ru.tinkoff.kora.config.annotation.processor;

import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.config.common.Config;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor;
import ru.tinkoff.kora.config.common.factory.MapConfigFactory;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class ConfigSourceAnnotationTest extends AbstractConfigTest {

    @Test
    public void testConfigSourceGeneratesConfigExtractor() {
        var extractor = this.compileConfig(List.of(), """
            @ru.tinkoff.kora.config.common.annotation.ConfigSource("test.path")
            public interface TestConfig {
              int value();
            }
            """);

        assertThat(extractor.extract(MapConfigFactory.fromMap(Map.of("value", 42)).root()))
            .isEqualTo(newObject("$TestConfig_ConfigValueExtractor$TestConfig_Impl", 42));
    }

    @Test
    public void testConfigSourceGeneratesModule() throws NoSuchMethodException {
        this.compileConfig(List.of(), """
            @ru.tinkoff.kora.config.common.annotation.ConfigSource("test.path")
            public interface TestConfig {
              int value();
            }
            """);

        var moduleClass = this.compileResult.loadClass("TestConfigModule");
        assertThat(moduleClass)
            .isNotNull()
            .isInterface()
            .hasMethods("testConfig");

        var method = moduleClass.getMethod("testConfig", Config.class, ConfigValueExtractor.class);
        assertThat(method).isNotNull();
        assertThat(method.getReturnType()).isEqualTo(this.compileResult.loadClass("TestConfig"));
        assertThat(method.isDefault()).isTrue();
    }

}
