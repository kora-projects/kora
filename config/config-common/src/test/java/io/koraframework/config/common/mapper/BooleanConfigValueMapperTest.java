package io.koraframework.config.common.mapper;

import io.koraframework.config.common.exception.ConfigValueException;
import io.koraframework.config.common.util.ConfigMappingUtils;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BooleanConfigValueMapperTest {
    private final BooleanConfigValueMapper mapper = new BooleanConfigValueMapper();

    @Test
    void testBooleanFromString() {
        var config = ConfigMappingUtils.fromMap(Map.of("t", "true", "f", "false", "b", true));

        assertThat(config.get("t").asBoolean()).isTrue();
        assertThat(config.get("f").asBoolean()).isFalse();
        assertThat(config.get("b").asBoolean()).isTrue();
        assertThat(mapper.map(config.get("t"))).isTrue();
        assertThat(mapper.map(config.get("f"))).isFalse();
        assertThat(mapper.map(config.get("b"))).isTrue();
    }

    @Test
    void testBooleanFromStringIgnoresCase() {
        var config = ConfigMappingUtils.fromMap(Map.of("upper", "TRUE", "mixed", "False"));

        assertThat(config.get("upper").asBoolean()).isTrue();
        assertThat(config.get("mixed").asBoolean()).isFalse();
        assertThat(mapper.map(config.get("upper"))).isTrue();
        assertThat(mapper.map(config.get("mixed"))).isFalse();
    }

    @Test
    void testNonBooleanStringRejected() {
        var config = ConfigMappingUtils.fromMap(Map.of("yes", "yes", "one", "1", "typo", "ture"));

        for (var key : new String[]{"yes", "one", "typo"}) {
            var value = config.get(key);
            assertThatThrownBy(value::asBoolean)
                .isInstanceOf(ConfigValueException.class)
                .hasMessageContaining("'BooleanValue'");
            assertThatThrownBy(() -> mapper.map(value))
                .isInstanceOf(ConfigValueException.class)
                .hasMessageContaining("'BooleanValue'");
        }
    }
}
