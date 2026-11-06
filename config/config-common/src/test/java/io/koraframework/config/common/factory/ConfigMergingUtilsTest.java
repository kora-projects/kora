package io.koraframework.config.common.factory;

import io.koraframework.config.common.util.ConfigMappingUtils;
import io.koraframework.config.common.util.ConfigMergingUtils;
import org.junit.jupiter.api.Test;
import io.koraframework.config.common.ConfigValue;
import io.koraframework.config.common.ConfigValuePath;

import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigMergingUtilsTest {
    @Test
    void testMergeRoots() {
        var config1 = ConfigMappingUtils.fromMap(Map.of(
            "field1", "value1"
        ));
        var config2 = ConfigMappingUtils.fromMap(Map.of(
            "field2", "value2"
        ));

        var config = ConfigMergingUtils.merge(config1, config2);

        assertThat(config.get(ConfigValuePath.root().child("field1")))
            .isInstanceOf(ConfigValue.StringValue.class)
            .hasFieldOrPropertyWithValue("value", "value1");
        assertThat(config.get(ConfigValuePath.root().child("field2")))
            .isInstanceOf(ConfigValue.StringValue.class)
            .hasFieldOrPropertyWithValue("value", "value2");
    }

    @Test
    void testFirstConfigFieldWins() {
        var config1 = ConfigMappingUtils.fromMap(Map.of(
            "field1", "value1"
        ));
        var config2 = ConfigMappingUtils.fromMap(Map.of(
            "field1", "value2"
        ));

        var config = ConfigMergingUtils.merge(config1, config2);

        assertThat(config.get(ConfigValuePath.root().child("field1")))
            .isInstanceOf(ConfigValue.StringValue.class)
            .hasFieldOrPropertyWithValue("value", "value1");
    }

    @Test
    void testSubobjectsMerge() {
        var config1 = ConfigMappingUtils.fromMap(Map.of(
            "field1", Map.of(
                "f1", "v1",
                "f2", "v2"
            )
        ));
        var config2 = ConfigMappingUtils.fromMap(Map.of(
            "field1", Map.of(
                "f2", "v3",
                "f3", "v4"
            )
        ));

        var config = ConfigMergingUtils.merge(config1, config2);

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

    @Test
    void testKebabCaseKeyOverridesCamelCaseKeyFromFallback() {
        var config1 = ConfigMappingUtils.fromMap(Map.of("db", Map.of("max-pool-size", "20")));
        var config2 = ConfigMappingUtils.fromMap(Map.of("db", Map.of("maxPoolSize", "10")));

        var config = ConfigMergingUtils.merge(config1, config2);

        assertThat(config.get(ConfigValuePath.root().child("db").child("maxPoolSize")))
            .isInstanceOf(ConfigValue.StringValue.class)
            .hasFieldOrPropertyWithValue("value", "20");
        assertThat(config.get(ConfigValuePath.root().child("db")).asObject().value()).containsOnlyKeys("max-pool-size", "maxPoolSize");
    }

    @Test
    void testSnakeCaseKeyOverridesCamelCaseKeyFromFallback() {
        var config1 = ConfigMappingUtils.fromMap(Map.of("db", Map.of("max_pool_size", "20")));
        var config2 = ConfigMappingUtils.fromMap(Map.of("db", Map.of("maxPoolSize", "10")));

        var config = ConfigMergingUtils.merge(config1, config2);

        assertThat(config.get(ConfigValuePath.root().child("db").child("maxPoolSize")))
            .isInstanceOf(ConfigValue.StringValue.class)
            .hasFieldOrPropertyWithValue("value", "20");
        assertThat(config.get(ConfigValuePath.root().child("db")).asObject().value()).containsOnlyKeys("max_pool_size", "maxPoolSize");
    }

    @Test
    void testCamelCaseKeyOverridesKebabCaseKeyFromFallback() {
        var config1 = ConfigMappingUtils.fromMap(Map.of("db", Map.of("maxPoolSize", "20")));
        var config2 = ConfigMappingUtils.fromMap(Map.of("db", Map.of("max-pool-size", "10")));

        var config = ConfigMergingUtils.merge(config1, config2);

        assertThat(config.get(ConfigValuePath.root().child("db").child("maxPoolSize")))
            .isInstanceOf(ConfigValue.StringValue.class)
            .hasFieldOrPropertyWithValue("value", "20");
    }

    @Test
    void testBothSpellingsInConfigOverrideCamelCaseKeyFromFallback() {
        var config1 = ConfigMappingUtils.fromMap(Map.of("db", Map.of("max-pool-size", "20", "max_pool_size", "30")));
        var config2 = ConfigMappingUtils.fromMap(Map.of("db", Map.of("maxPoolSize", "10")));

        var config = ConfigMergingUtils.merge(config1, config2);

        assertThat(config.get(ConfigValuePath.root().child("db").child("maxPoolSize")))
            .isInstanceOf(ConfigValue.StringValue.class)
            .hasFieldOrPropertyWithValue("value", "20");
        assertThat(config.get(ConfigValuePath.root().child("db")).asObject().value()).containsOnlyKeys("max-pool-size", "max_pool_size", "maxPoolSize");
    }

    @Test
    void testKebabCaseScalarDoesNotDropCamelCaseObjectFromFallback() {
        var config1 = ConfigMappingUtils.fromMap(Map.of("max-pool", "20"));
        var config2 = ConfigMappingUtils.fromMap(Map.of("maxPool", Map.of("size", "10")));

        var config = ConfigMergingUtils.merge(config1, config2);

        assertThat(config.get(ConfigValuePath.root().child("maxPool").child("size")))
            .isInstanceOf(ConfigValue.StringValue.class)
            .hasFieldOrPropertyWithValue("value", "10");
    }

    @Test
    void testUpperCaseEnvVariableDoesNotDropLowerCaseSectionFromFallback() {
        var properties = new Properties();
        properties.setProperty("user.home", "/home/alice");
        var environment = ConfigMappingUtils.fromMap(Map.of("USER", "alice", "LOGGING", "1"));
        var fallback = ConfigMergingUtils.merge(
            ConfigMappingUtils.fromProperties(properties),
            ConfigMappingUtils.fromMap(Map.of("logging", Map.of("level", "info")))
        );

        var config = ConfigMergingUtils.merge(environment, fallback);

        assertThat(config.get(ConfigValuePath.root().child("user").child("home")))
            .isInstanceOf(ConfigValue.StringValue.class)
            .hasFieldOrPropertyWithValue("value", "/home/alice");
        assertThat(config.get(ConfigValuePath.root().child("logging").child("level")))
            .isInstanceOf(ConfigValue.StringValue.class)
            .hasFieldOrPropertyWithValue("value", "info");
        assertThat(config.get(ConfigValuePath.root().child("USER")))
            .isInstanceOf(ConfigValue.StringValue.class)
            .hasFieldOrPropertyWithValue("value", "alice");
    }

    @Test
    void testMapEntriesSpelledDifferentlyInBothLayersAreKept() {
        var config1 = ConfigMappingUtils.fromMap(Map.of("headers", Map.of("request-id", "a")));
        var config2 = ConfigMappingUtils.fromMap(Map.of("headers", Map.of("requestId", "b")));

        var config = ConfigMergingUtils.merge(config1, config2);

        var headers = config.get(ConfigValuePath.root().child("headers")).asObject().value();
        assertThat(headers).containsOnlyKeys("request-id", "requestId");
        assertThat(headers.get("request-id").asString()).isEqualTo("a");
        assertThat(headers.get("requestId").asString()).isEqualTo("b");
    }

    @Test
    void testOverrideSurvivesResolveAndFurtherMerges() {
        var config1 = ConfigMappingUtils.fromMap(Map.of("db", Map.of("max-pool-size", "${size}"), "size", "20"));
        var config2 = ConfigMappingUtils.fromMap(Map.of("db", Map.of("maxPoolSize", "10")));
        var config3 = ConfigMappingUtils.fromMap(Map.of("db", Map.of("maxPoolSize", "5")));
        var top = ConfigMappingUtils.fromMap(Map.of("db", Map.of("maxPoolSize", "30")));
        var maxPoolSize = ConfigValuePath.root().child("db").child("maxPoolSize");

        var lower = ConfigMergingUtils.merge(ConfigMergingUtils.merge(config1, config2), config3).resolve();
        assertThat(lower.get(maxPoolSize).asString()).isEqualTo("20");

        var config = ConfigMergingUtils.merge(top, lower);
        assertThat(config.get(maxPoolSize).asString()).isEqualTo("30");
    }
}
