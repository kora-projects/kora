package io.koraframework.config.common;

import org.junit.jupiter.api.Test;
import io.koraframework.config.common.mapper.PropertiesConfigValueMapper;
import io.koraframework.config.common.util.ConfigMappingUtils;

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
}
