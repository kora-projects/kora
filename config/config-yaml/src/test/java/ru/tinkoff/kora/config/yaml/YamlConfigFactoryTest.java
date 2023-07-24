package ru.tinkoff.kora.config.yaml;

import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.config.common.ConfigValue;
import ru.tinkoff.kora.config.common.origin.SimpleConfigOrigin;

import java.io.ByteArrayInputStream;
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
}
