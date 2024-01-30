package ru.tinkoff.kora.config.common.impl;

import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.config.common.ConfigValue;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static ru.tinkoff.kora.config.common.factory.MapConfigFactory.fromMap;

class ConfigResolverTest {
    @Test
    void testResolveReference() {
        var config = fromMap(Map.of(
            "object", Map.of(
                "field", "test-value"
            ),
            "reference", "${object.field}"
        )).resolve();
        assertThat(config.get("reference").asString()).isEqualTo("test-value");
    }

    @Test
    void testResolveReferenceWithDefault() {
        var config = fromMap(Map.of(
            "reference", "${object.field:default-value}"
        )).resolve();
        assertThat(config.get("reference").asString()).isEqualTo("default-value");
    }

    @Test
    void testNullableReference() {
        var config = fromMap(Map.of(
            "reference", "${?object.field}"
        )).resolve();
        assertThat(config.get("reference")).isInstanceOf(ConfigValue.NullValue.class);
    }

    @Test
    void testMultipleValues() {
        var config = fromMap(Map.of(
            "value", "value",
            "reference", "value: ${value}, nullableValue1: ${?value}, nullableValue2: ${?value2}, valueWithDefault: ${value:default}, valueWithDefault: ${value2:default} leftover"
        )).resolve();
        assertThat(config.get("reference"))
            .isInstanceOf(ConfigValue.StringValue.class)
            .extracting("value", InstanceOfAssertFactories.STRING)
            .isEqualTo("value: value, nullableValue1: value, nullableValue2: , valueWithDefault: value, valueWithDefault: default leftover");
    }
}
