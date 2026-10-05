package io.koraframework.config.common.impl;

import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import io.koraframework.config.common.util.ConfigMappingUtils;
import io.koraframework.config.common.ConfigValue;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static io.koraframework.config.common.util.ConfigMappingUtils.fromMap;

class ConfigResolverUtilsTest {
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

    @Test
    void testReferenceCycle() {
        var config = fromMap(Map.of(
            "a", "${b}",
            "b", "${a}"
        ));
        assertThatThrownBy(config::resolve)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("cycle");
    }

    @Test
    void testSelfReference() {
        assertThatThrownBy(() -> fromMap(Map.of("a", "${a}")).resolve())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("cycle");
        assertThatThrownBy(() -> fromMap(Map.of("a", "${?a}")).resolve())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("cycle");
        assertThatThrownBy(() -> fromMap(Map.of("a", "${a:1}")).resolve())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("cycle");
    }

    @Test
    void testSameReferenceTwiceIsNotCycle() {
        var config = fromMap(Map.of(
            "value", "v",
            "a", "${value}-${value}",
            "b", "${a}/${a}"
        )).resolve();
        assertThat(config.get("b").asString()).isEqualTo("v-v/v-v");
    }

    @Test
    void testArrayWithNullElement() {
        var properties = new java.util.Properties();
        properties.setProperty("list[1]", "x");
        var config = ConfigMappingUtils.fromProperties(properties).resolve();
        var list = config.get("list").asArray().value();
        assertThat(list).hasSize(2);
        assertThat(list.get(0)).isInstanceOf(ConfigValue.NullValue.class);
        assertThat(list.get(0).origin().path()).hasToString("ROOT.list.0");
        assertThat(list.get(1).asString()).isEqualTo("x");
    }
}
