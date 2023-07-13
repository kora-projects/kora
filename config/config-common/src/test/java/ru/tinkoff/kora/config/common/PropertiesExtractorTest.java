package ru.tinkoff.kora.config.common;

import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.config.common.extractor.PropertiesConfigValueExtractor;
import ru.tinkoff.kora.config.common.factory.MapConfigFactory;

import java.util.Map;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class PropertiesExtractorTest {
    private final Config config = MapConfigFactory.fromMap(Map.of(
        "properties", Map.of(
            "bootstrap.servers", "localhost:9092",
            "password", "test_password"
        )
    ));

    @Test
    void testPropertiesExtractor() {
        var propertiesExtractor = new PropertiesConfigValueExtractor();
        var properties = propertiesExtractor.extract(config.get(ConfigValuePath.root().child("properties")));
        assertThat(properties.get("password")).isEqualTo("test_password");
        assertThat(properties.getProperty("bootstrap.servers")).isEqualTo("localhost:9092");
    }
}
