package io.koraframework.config.yaml;

import org.junit.jupiter.api.Test;
import io.koraframework.config.common.ConfigValue;
import io.koraframework.config.common.mapper.ConfigValueMapperModule;
import io.koraframework.config.common.origin.SimpleConfigOrigin;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class YamlConfigFactoryTest {
    @Test
    void testEmptyYaml() {
        var config = YamlConfigFactory.fromYaml(new SimpleConfigOrigin(""), new ByteArrayInputStream(new byte[0]));
        assertThat(config.root()).isEmpty();
    }

    @Test
    void testYaml() {
        var yaml = """
            test:
              test:
                test: value
            """;
        var config = YamlConfigFactory.fromYaml(new SimpleConfigOrigin(""), new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));
        assertThat(config.get("test.test.test")).isInstanceOf(ConfigValue.StringValue.class);
    }

    @Test
    void testFractionalNumberKeepsAllDigits() {
        var yaml = """
            amount: 12345678901234567.89
            negative: -0.1
            """;
        var config = YamlConfigFactory.fromYaml(new SimpleConfigOrigin(""), new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));
        var mapper = new ConfigValueMapperModule() {}.bigDecimalConfigValueMapper();

        assertThat(mapper.map(config.get("amount"))).isEqualTo(new BigDecimal("12345678901234567.89"));
        assertThat(mapper.map(config.get("negative"))).isEqualTo(new BigDecimal("-0.1"));
    }
}
