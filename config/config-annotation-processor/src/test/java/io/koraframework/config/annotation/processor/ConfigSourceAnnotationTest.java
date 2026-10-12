package io.koraframework.config.annotation.processor;

import io.koraframework.config.common.mapper.ConfigValueMapper;
import io.koraframework.validation.common.Validator;
import org.junit.jupiter.api.Test;
import io.koraframework.config.common.Config;
import io.koraframework.config.common.util.ConfigMappingUtils;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

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
    public void testValidConfigSourceValidatedAfterParse() {
        var validator = Mockito.mock(Validator.class);
        var mapper = this.compileConfig(List.of(validator), """
            @io.koraframework.config.common.annotation.ConfigSource("test.path")
            @io.koraframework.validation.common.annotation.Valid
            public interface TestConfig {
              @io.koraframework.validation.common.annotation.NotBlank
              String value();
            }
            """);

        var result = mapper.map(ConfigMappingUtils.fromMap(Map.of("value", "test")).root());

        assertThat(result)
            .isEqualTo(newObject("$TestConfig_ConfigValueMapper$TestConfig_Impl", "test"));
        verify(validator).validateAndThrow(result);
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

    @Test
    public void testConfigWithPatternAndArrayMapsSameValueToEqualObjects() {
        var patternMapper = new io.koraframework.config.common.mapper.PatternConfigValueMapper();
        ConfigValueMapper<int[]> intsMapper = v -> new int[]{1, 2};
        var mapper = this.compileConfig(List.of(patternMapper, intsMapper), """
            @io.koraframework.config.common.annotation.ConfigSource("test.path")
            public interface TestConfig {
              java.util.regex.Pattern pattern();
              java.util.regex.@Nullable Pattern nullablePattern();
              int[] ints();
            }
            """);
        var value = Map.of("pattern", "orders-.*", "ints", List.of(1, 2));

        var first = mapper.map(ConfigMappingUtils.fromMap(value).root());
        var second = mapper.map(ConfigMappingUtils.fromMap(value).root());
        var other = mapper.map(ConfigMappingUtils.fromMap(Map.of("pattern", "payments-.*", "ints", List.of(1, 2))).root());

        assertThat(second).isEqualTo(first).hasSameHashCodeAs(first);
        assertThat(other).isNotEqualTo(first);
    }
}
