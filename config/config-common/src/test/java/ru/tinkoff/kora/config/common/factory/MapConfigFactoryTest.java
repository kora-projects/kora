package ru.tinkoff.kora.config.common.factory;

import org.assertj.core.api.Condition;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.assertj.core.data.Index;
import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.config.common.ConfigValue;
import ru.tinkoff.kora.config.common.ConfigValuePath;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class MapConfigFactoryTest {
    @Test
    void testFromMap() {
        var config = MapConfigFactory.fromMap(Map.of(
            "field1", "value1",
            "field2", 2,
            "field3", List.of(1, "2"),
            "field4", Map.of(
                "f1", 1
            )
        ));

        assertThat(config.get(ConfigValuePath.root().child("field1")))
            .isInstanceOf(ConfigValue.StringValue.class)
            .hasFieldOrPropertyWithValue("value", "value1");
        assertThat(config.get(ConfigValuePath.root().child("field2")))
            .isInstanceOf(ConfigValue.NumberValue.class)
            .hasFieldOrPropertyWithValue("value", 2);

        assertThat(config.get(ConfigValuePath.root().child("field3")))
            .isInstanceOf(ConfigValue.ArrayValue.class);
        assertThat(config.get(ConfigValuePath.root().child("field3").child(0)))
            .isInstanceOf(ConfigValue.NumberValue.class)
            .hasFieldOrPropertyWithValue("value", 1);
        assertThat(config.get(ConfigValuePath.root().child("field3").child(1)))
            .isInstanceOf(ConfigValue.StringValue.class)
            .hasFieldOrPropertyWithValue("value", "2");

        assertThat(config.get(ConfigValuePath.root().child("field4")))
            .isInstanceOf(ConfigValue.ObjectValue.class);
        assertThat(config.get(ConfigValuePath.root().child("field4").child("f1")))
            .isInstanceOf(ConfigValue.NumberValue.class)
            .hasFieldOrPropertyWithValue("value", 1);
    }

    @Test
    void testFromProperties() {
        var properties = new Properties();
        properties.setProperty("object1.field1", "1");
        properties.setProperty("object1.field2", "2");
        properties.setProperty("object2.field1[0]", "3");
        properties.setProperty("object2.field1[1]", "4");
        properties.setProperty("object2.field1[3]", "5");
        properties.setProperty("object2.field1[4].field1", "7");
        properties.setProperty("object3", "6");

        var config = MapConfigFactory.fromProperties(properties);


        assertThat(config.get("object1.field1"))
            .isInstanceOf(ConfigValue.StringValue.class)
            .hasFieldOrPropertyWithValue("value", "1");

        assertThat(config.get("object1.field2"))
            .isInstanceOf(ConfigValue.StringValue.class)
            .hasFieldOrPropertyWithValue("value", "2");

        assertThat(config.get("object2.field1"))
            .asInstanceOf(InstanceOfAssertFactories.iterable(ConfigValue.class))
            .map(v -> v == null ? null : v.value())
            .has(new Condition<>(v -> v.equals("3"), ""), Index.atIndex(0))
            .has(new Condition<>(v -> v.equals("4"), ""), Index.atIndex(1))
            .has(new Condition<>(Objects::isNull, ""), Index.atIndex(2))
            .has(new Condition<>(v -> v.equals("5"), ""), Index.atIndex(3))
            .has(new Condition<>(v -> v instanceof Map<?, ?> map && map.size() == 1 && map.get("field1") instanceof ConfigValue.StringValue str && str.value().equals("7"), ""), Index.atIndex(4));

        assertThat(config.get("object3"))
            .isInstanceOf(ConfigValue.StringValue.class)
            .hasFieldOrPropertyWithValue("value", "6");
    }
}
