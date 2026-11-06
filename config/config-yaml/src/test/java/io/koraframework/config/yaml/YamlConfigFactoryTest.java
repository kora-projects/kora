package io.koraframework.config.yaml;

import org.junit.jupiter.api.Test;
import io.koraframework.config.common.ConfigValue;
import io.koraframework.config.common.origin.SimpleConfigOrigin;

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

    @Test
    void testNonStringKeys() {
        var yaml = """
            errors:
              404: not-found
              true: yes
            """;
        var config = YamlConfigFactory.fromYaml(new SimpleConfigOrigin(""), new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));
        assertThat(config.get("errors.404").asString()).isEqualTo("not-found");
        assertThat(config.get("errors").asObject().value()).containsOnlyKeys("404", "true");
    }

    @Test
    void testListWithEmptyItem() {
        var yaml = """
            hosts:
              - a
              -
              - b
            """;
        var config = YamlConfigFactory.fromYaml(new SimpleConfigOrigin(""), new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8))).resolve();
        var hosts = config.get("hosts").asArray().value();
        assertThat(hosts).hasSize(3);
        assertThat(hosts.get(1)).isInstanceOf(ConfigValue.NullValue.class);
        assertThat(hosts.get(1).origin().path()).hasToString("ROOT.hosts.1");
    }
}
