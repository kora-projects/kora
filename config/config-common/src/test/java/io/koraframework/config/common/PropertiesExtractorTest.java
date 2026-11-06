package io.koraframework.config.common;

import org.junit.jupiter.api.Test;
import io.koraframework.config.common.mapper.PropertiesConfigValueMapper;
import io.koraframework.config.common.util.ConfigMappingUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class PropertiesExtractorTest {
    private final Config config = ConfigMappingUtils.fromMap(Map.of(
        "properties", Map.of(
            "bootstrap.servers", "localhost:9092",
            "password", "test_password"
        )
    ));

    @Test
    void testPropertiesExtractor() {
        var propertiesExtractor = new PropertiesConfigValueMapper();
        var properties = propertiesExtractor.map(config.get(ConfigValuePath.root().child("properties")));
        assertThat(properties.get("password")).isEqualTo("test_password");
        assertThat(properties.getProperty("bootstrap.servers")).isEqualTo("localhost:9092");
    }

    @Test
    void testPropertiesExtractorStoresScalarsAsStrings() {
        var config = ConfigMappingUtils.fromMap(Map.of(
            "properties", Map.of(
                "org.quartz.threadPool.threadCount", 10,
                "org.quartz.jobStore.isClustered", true,
                "hosts", List.of("a", 1)
            )
        ));
        var properties = new PropertiesConfigValueMapper().map(config.get(ConfigValuePath.root().child("properties")));
        assertThat(properties.getProperty("org.quartz.threadPool.threadCount")).isEqualTo("10");
        assertThat(properties.getProperty("org.quartz.jobStore.isClustered")).isEqualTo("true");
        assertThat(properties.get("hosts")).isEqualTo(List.of("a", "1"));
    }

    @Test
    void testPropertiesExtractorSkipsNullValues() {
        var origin = config.get(ConfigValuePath.root()).origin();
        var value = new ConfigValue.ObjectValue(origin, Map.of(
            "bootstrap.servers", new ConfigValue.StringValue(origin, "localhost:9092"),
            "sasl.jaas.config", new ConfigValue.NullValue(origin),
            "hosts", new ConfigValue.ArrayValue(origin, List.of(new ConfigValue.StringValue(origin, "a"), new ConfigValue.NullValue(origin)))
        ));
        var properties = new PropertiesConfigValueMapper().map(value);
        assertThat(properties.getProperty("bootstrap.servers")).isEqualTo("localhost:9092");
        assertThat(properties.containsKey("sasl.jaas.config")).isFalse();
        assertThat(properties.get("hosts")).isEqualTo(List.of("a"));
    }
}
