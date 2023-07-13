package ru.tinkoff.kora.config.common.factory;

import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.config.common.ConfigValue;
import ru.tinkoff.kora.config.common.ConfigValuePath;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MergeConfigFactoryTest {
    @Test
    void testMergeRoots() {
        var config1 = MapConfigFactory.fromMap(Map.of(
            "field1", "value1"
        ));
        var config2 = MapConfigFactory.fromMap(Map.of(
            "field2", "value2"
        ));

        var config = MergeConfigFactory.merge(config1, config2);

        assertThat(config.get(ConfigValuePath.root().child("field1")))
            .isInstanceOf(ConfigValue.StringValue.class)
            .hasFieldOrPropertyWithValue("value", "value1");
        assertThat(config.get(ConfigValuePath.root().child("field2")))
            .isInstanceOf(ConfigValue.StringValue.class)
            .hasFieldOrPropertyWithValue("value", "value2");
    }

    @Test
    void testFirstConfigFieldWins() {
        var config1 = MapConfigFactory.fromMap(Map.of(
            "field1", "value1"
        ));
        var config2 = MapConfigFactory.fromMap(Map.of(
            "field1", "value2"
        ));

        var config = MergeConfigFactory.merge(config1, config2);

        assertThat(config.get(ConfigValuePath.root().child("field1")))
            .isInstanceOf(ConfigValue.StringValue.class)
            .hasFieldOrPropertyWithValue("value", "value1");
    }

    @Test
    void testSubobjectsMerge() {
        var config1 = MapConfigFactory.fromMap(Map.of(
            "field1", Map.of(
                "f1", "v1",
                "f2", "v2"
            )
        ));
        var config2 = MapConfigFactory.fromMap(Map.of(
            "field1", Map.of(
                "f2", "v3",
                "f3", "v4"
            )
        ));

        var config = MergeConfigFactory.merge(config1, config2);

        assertThat(config.get(ConfigValuePath.root().child("field1").child("f1")))
            .isInstanceOf(ConfigValue.StringValue.class)
            .hasFieldOrPropertyWithValue("value", "v1");
        assertThat(config.get(ConfigValuePath.root().child("field1").child("f2")))
            .isInstanceOf(ConfigValue.StringValue.class)
            .hasFieldOrPropertyWithValue("value", "v2");
        assertThat(config.get(ConfigValuePath.root().child("field1").child("f3")))
            .isInstanceOf(ConfigValue.StringValue.class)
            .hasFieldOrPropertyWithValue("value", "v4");
    }
}
